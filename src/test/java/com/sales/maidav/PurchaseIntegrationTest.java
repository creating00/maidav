package com.sales.maidav;

import com.sales.maidav.model.purchase.Purchase;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.purchase.*;
import com.sales.maidav.web.dto.PurchaseForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Opt-in: only a disposable, local database with this exact name is accepted.
@EnabledIfEnvironmentVariable(named = "MAIDAV_PURCHASES_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/maidav_purchases_test")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("purchases-test")
class PurchaseIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MAIDAV_PURCHASES_TEST_URL"));
        registry.add("spring.datasource.username", () -> "purchases_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired PurchaseService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long userId;
    Long providerId;
    Long productId;
    String email;

    @BeforeEach
    void fixtures() {
        email = "purchases-" + UUID.randomUUID() + "@example.test";
        userId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, email);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'", userId);
        providerId = jdbc.queryForObject("insert into providers(name,created_at) values ('Proveedor de prueba',now()) returning id", Long.class);
        productId = product(10);
    }

    @Test
    void migratesCreatesAndRendersAllPagesWithSnapshotsAndCombinedFilters() throws Exception {
        jdbc.update("update products set barcode=? where id=?", "COMPRA-" + productId, productId);
        for (int index = 0; index < 7; index++) product(index);
        PurchaseForm form = form(productId, 3);
        form.setItems(List.of(item(productId, 3), item(productId, 2)));
        Purchase purchase = service.create(form, userId);
        assertThat(stock(productId)).isEqualTo(15);
        assertThat(purchase.getTotalAmount()).isEqualByComparingTo("50.75");
        assertThat(service.findPage(LocalDate.now(), LocalDate.now(), providerId, productId, 0).getTotalElements()).isEqualTo(1);
        assertThat(service.findPage(null, null, null, -1L, 0)).isEmpty();
        assertThat(jdbc.queryForObject("select cost from products where id=?", BigDecimal.class, productId)).isEqualByComparingTo("20");
        jdbc.update("update products set description='Descripción cambiada' where id=?", productId);
        jdbc.update("update providers set name='Proveedor cambiado' where id=?", providerId);
        assertThat(service.findById(purchase.getId()).getItems().getFirst().getProductDescription()).isEqualTo("Producto de prueba");
        savePreview("index", mvc.perform(get("/purchases").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Finanzas")))
                .andReturn().getResponse().getContentAsString());
        savePreview("form", mvc.perform(get("/purchases/new").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("items[0].productId")))
                .andReturn().getResponse().getContentAsString());
        savePreview("detail", mvc.perform(get("/purchases/" + purchase.getId()).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Proveedor de prueba")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/sales/new").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"productSearch\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Volver a la venta")));
    }

    @Test
    void rollsBackAllStockAndRequestKeyWhenLaterProductCannotBeUpdated() {
        Long fullProduct = product(Integer.MAX_VALUE);
        PurchaseForm form = form(productId, 2);
        form.setItems(List.of(item(productId, 2), item(fullProduct, 1)));
        assertThatThrownBy(() -> service.create(form, userId)).isInstanceOf(InvalidPurchaseException.class);
        assertThat(stock(productId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from purchase_idempotency_keys where user_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from purchases where registered_by_id=?", Long.class, userId)).isZero();
    }

    @Test
    void concurrentDuplicateRequestsOnlyCreateOnePurchase() throws Exception {
        PurchaseForm form = form(productId, 3);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Long> create = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.create(form, userId).getId();
            };
            Future<Long> first = executor.submit(create);
            Future<Long> second = executor.submit(create);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(stock(productId)).isEqualTo(13);
        assertThat(jdbc.queryForObject("select count(*) from purchases where registered_by_id=?", Long.class, userId)).isEqualTo(1);
    }

    @Test
    void protectsRoutesAndCsrfAndPreservesInvalidFormInput() throws Exception {
        String restrictedEmail = "restricted-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users(email,password) values (?, 'test-unused')", restrictedEmail);
        mvc.perform(get("/purchases").with(user(restrictedEmail))).andExpect(status().isForbidden());
        mvc.perform(get("/purchases/new").with(user(restrictedEmail))).andExpect(status().isForbidden());
        mvc.perform(get("/purchases/1").with(user(restrictedEmail))).andExpect(status().isForbidden());
        mvc.perform(post("/purchases").with(user(restrictedEmail)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/purchases").with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/purchases").with(user(email).roles("ADMIN")).with(csrf())
                        .param("operationKey", UUID.randomUUID().toString()).param("purchaseDate", LocalDate.now().toString())
                        .param("paymentMethod", "CASH").param("notes", "Conservar esta observación")
                        .param("items[0].productId", productId.toString()).param("items[0].quantity", "no-es-numero")
                        .param("items[0].unitPrice", "10.15"))
                .andExpect(status().isOk()).andExpect(view().name("pages/purchases/form"))
                .andExpect(model().attribute("purchaseForm", org.hamcrest.Matchers.hasProperty("notes",
                        org.hamcrest.Matchers.equalTo("Conservar esta observación"))));
        assertThat(stock(productId)).isEqualTo(10);
    }

    @Test
    void createsThroughPostAndRejectsInvalidDateFilterWithoutServerError() throws Exception {
        mvc.perform(post("/purchases").with(user(email).roles("ADMIN")).with(csrf())
                        .param("operationKey", UUID.randomUUID().toString()).param("purchaseDate", LocalDate.now().toString())
                        .param("paymentMethod", "CASH").param("items[0].productId", productId.toString())
                        .param("items[0].quantity", "2").param("items[0].unitPrice", "10.15"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/purchases/*"));
        assertThat(stock(productId)).isEqualTo(12);
        mvc.perform(get("/purchases").with(user(email).roles("ADMIN"))
                        .param("from", "2026-11-19").param("to", "2026-11-18"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
    }

    private Long product(int stock) {
        return jdbc.queryForObject("""
                insert into products(provider_id,product_code,description,cost,price_wholesale_net,price_retail_net,
                    vat_rate,price_wholesale,price_retail,stock_available,stock_min,stock_max,created_at)
                values (?,?,'Producto de prueba',20,30,40,0,30,40,?,0,100,now()) returning id
                """, Long.class, providerId, UUID.randomUUID().toString(), stock);
    }

    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target/purchases-preview");
        Files.createDirectories(directory);
        String resources = Path.of("src/main/resources/static").toAbsolutePath().toUri().toString();
        html = html.replace("src=\"/", "src=\"" + resources).replace("href=\"/", "href=\"" + resources);
        Files.writeString(directory.resolve(name + ".html"), html);
    }

    private int stock(Long id) {
        return jdbc.queryForObject("select stock_available from products where id=?", Integer.class, id);
    }

    private PurchaseForm form(Long id, int quantity) {
        var form = new PurchaseForm();
        form.setPurchaseDate(LocalDate.now());
        form.setProviderId(providerId);
        form.setPaymentMethod(PaymentCollectionMethod.CASH);
        form.setOperationKey(UUID.randomUUID().toString());
        form.setItems(List.of(item(id, quantity)));
        return form;
    }

    private PurchaseForm.Item item(Long id, int quantity) {
        var item = new PurchaseForm.Item();
        item.setProductId(id);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("10.15"));
        return item;
    }
}
