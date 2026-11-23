package com.sales.maidav;

import com.sales.maidav.model.campaign.*;
import com.sales.maidav.model.sale.*;
import com.sales.maidav.repository.client.ClientRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.campaign.*;
import com.sales.maidav.service.sale.*;
import com.sales.maidav.service.finance.FinanceService;
import com.sales.maidav.web.dto.CampaignForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "MAIDAV_PURCHASES_TEST_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/maidav_purchases_test")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("purchases-test")
class CampaignIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MAIDAV_PURCHASES_TEST_URL"));
        registry.add("spring.datasource.username", () -> "purchases_test");
        registry.add("spring.datasource.password", () -> "");
    }
    @Autowired CampaignService service;
    @Autowired SaleService saleService;
    @Autowired SaleItemRepository saleItems;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired FinanceService financeService;
    Long userId;
    Long clientId;
    Long productId;
    String email;

    @BeforeEach
    void fixtures() {
        email = "campaign-" + UUID.randomUUID() + "@example.test";
        userId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, email);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'", userId);
        clientId = jdbc.queryForObject("insert into clients(national_id,first_name,last_name,seller_id,created_at) values (?,'Cliente','Campaña',?,now()) returning id", Long.class,
                UUID.randomUUID().toString().replace("-", "").substring(0, 18), userId);
        Long provider = jdbc.queryForObject("insert into providers(name,created_at) values ('Proveedor campaña',now()) returning id", Long.class);
        productId = jdbc.queryForObject("insert into products(provider_id,product_code,description,cost,price_wholesale_net,price_retail_net,vat_rate,price_wholesale,price_retail,stock_available,stock_min,stock_max,created_at) values (?,?,'Producto campaña',200,800,1000,0,800,1000,10,0,100,now()) returning id", Long.class, provider, UUID.randomUUID().toString());
    }

    @Test
    void createsPercentageAndFixedOffersWithFrozenRetailReferenceAndIdempotency() {
        CampaignForm form = form();
        Campaign fixed = service.create(form, userId);
        assertThat(service.create(form, userId).getId()).isEqualTo(fixed.getId());
        assertThat(fixed.getItems().getFirst().getOfferPrice()).isEqualByComparingTo("800");
        assertThat(stock()).isEqualTo(10);
        jdbc.update("update products set price_retail=100.05 where id=?", productId);
        CampaignForm percent = form();
        percent.getItems().getFirst().setDiscountMode(DiscountMode.PERCENTAGE);
        percent.getItems().getFirst().setDiscountValue(new BigDecimal("10"));
        Campaign percentage = service.create(percent, userId);
        assertThat(percentage.getItems().getFirst().getOfferPrice()).isEqualByComparingTo("90.05");
        assertThat(service.findOffer(fixed.getItems().getFirst().getId()).getNormalPrice()).isEqualByComparingTo("1000");
        assertThat(service.findOffer(fixed.getItems().getFirst().getId()).getOfferPrice()).isEqualByComparingTo("800");
    }

    @ParameterizedTest
    @CsvSource({"PERCENTAGE,100", "PERCENTAGE,0", "FIXED_PRICE,1000", "FIXED_PRICE,-1", "FIXED_PRICE,1.001"})
    void rejectsInvalidDiscountsWithoutPersistingCampaignOrRequest(String mode, String value) {
        CampaignForm form = form();
        form.getItems().getFirst().setDiscountMode(DiscountMode.valueOf(mode));
        form.getItems().getFirst().setDiscountValue(new BigDecimal(value));
        assertThatThrownBy(() -> service.create(form, userId)).isInstanceOf(InvalidCampaignException.class);
        assertThat(jdbc.queryForObject("select count(*) from campaigns where registered_by_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from campaign_idempotency_keys where user_id=?", Long.class, userId)).isZero();
    }

    @Test
    void rejectsDuplicateProductsAndInvalidDates() {
        CampaignForm repeated = form();
        repeated.getItems().add(repeated.getItems().getFirst());
        assertThatThrownBy(() -> service.create(repeated, userId)).hasMessageContaining("sin repetir");
        CampaignForm dates = form();
        dates.setEndDate(dates.getStartDate().minusDays(1));
        assertThatThrownBy(() -> service.create(dates, userId)).hasMessageContaining("fechas");
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void derivesStatesWithInclusiveDatesAndCancellationPreventsSale() {
        CampaignForm today = form(); today.setEndDate(LocalDate.now());
        Campaign active = service.create(today, userId);
        assertThat(active.getState()).isEqualTo(CampaignFilter.ACTIVE);
        CampaignForm futureForm = form(); futureForm.setStartDate(LocalDate.now().plusDays(1));
        Campaign future = service.create(futureForm, userId);
        assertThat(future.getState()).isEqualTo(CampaignFilter.FUTURE);
        CampaignForm expiredForm = form(); expiredForm.setStartDate(LocalDate.now().minusDays(2)); expiredForm.setEndDate(LocalDate.now().minusDays(1));
        Campaign expired = service.create(expiredForm, userId);
        assertThat(expired.getState()).isEqualTo(CampaignFilter.EXPIRED);
        assertThat(service.findPage(CampaignFilter.ACTIVE, 0).getContent()).extracting(Campaign::getId).contains(active.getId()).doesNotContain(future.getId(), expired.getId());
        service.cancel(active.getId(), userId);
        assertThat(service.findById(active.getId()).getState()).isEqualTo(CampaignFilter.CANCELLED);
        assertThatThrownBy(() -> service.lockActiveOffer(active.getItems().getFirst().getId())).hasMessageContaining("no está activa");
        assertThatThrownBy(() -> service.findActiveOffer(expired.getItems().getFirst().getId())).hasMessageContaining("no está activa");
    }

    @Test
    void rendersCampaignPagesAndPrefillsSaleWithoutChangingStock() throws Exception {
        Campaign campaign = service.create(form(), userId);
        Long itemId = campaign.getItems().getFirst().getId();
        savePreview("index", mvc.perform(get("/campaigns").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        savePreview("form", mvc.perform(get("/campaigns/new").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        savePreview("detail", mvc.perform(get("/campaigns/" + campaign.getId()).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("campaignItemId=" + itemId)))
                .andReturn().getResponse().getContentAsString());
        savePreview("sale", mvc.perform(get("/sales/new").with(user(email).roles("ADMIN")).param("campaignItemId", itemId.toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("campaignOffer"))
                .andExpect(content().string(containsString("id=\"campaignSaleData\"")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/dashboard").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(model().attributeExists("activeOffers"))
                .andExpect(content().string(containsString("Ofertas activas de contado")));
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void confirmsSaleWithHistoricalTraceAndPreservesItAfterCancellation() throws Exception {
        Campaign campaign = service.create(form(), userId);
        Long itemId = campaign.getItems().getFirst().getId();
        String key = UUID.randomUUID().toString();
        BigDecimal incomeBefore = financeService.buildReport(LocalDate.now(), LocalDate.now(), 0).getIncome();
        mvc.perform(salePost(itemId, key, "800")).andExpect(status().is3xxRedirection());
        assertThat(financeService.buildReport(LocalDate.now(), LocalDate.now(), 0).getIncome()).isEqualByComparingTo(incomeBefore.add(new BigDecimal("800")));
        Long saleId = jdbc.queryForObject("select sale_id from sale_idempotency_keys where user_id=? and idempotency_key=?", Long.class, userId, key);
        SaleItem sold = saleItems.findBySale_IdOrderByIdAsc(saleId).getFirst();
        assertThat(sold.getCampaignItemId()).isEqualTo(itemId);
        assertThat(sold.getCampaignName()).isEqualTo(campaign.getName());
        assertThat(sold.getCampaignNormalPrice()).isEqualByComparingTo("1000");
        assertThat(sold.getUnitPrice()).isEqualByComparingTo("800");
        assertThat(stock()).isEqualTo(9);
        service.cancel(campaign.getId(), userId);
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "800")).andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        mvc.perform(salePost(itemId, key, "800")).andExpect(status().is3xxRedirection());
        assertThat(stock()).isEqualTo(9);
        mvc.perform(get("/sales/" + saleId).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Campaña:")))
                .andExpect(content().string(not(containsString("href=\"/sales/" + saleId + "/edit\""))));
        mvc.perform(get("/sales/" + saleId + "/edit").with(user(email).roles("ADMIN")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/sales/" + saleId));
        mvc.perform(post("/sales/" + saleId + "/void").with(user(email).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(stock()).isEqualTo(10);
        assertThat(financeService.buildReport(LocalDate.now(), LocalDate.now(), 0).getIncome()).isEqualByComparingTo(incomeBefore);
    }

    @Test
    void salespersonCanUseAuthorizedOfferWithoutAdministratorDiscountPrivileges() throws Exception {
        Campaign campaign = service.create(form(), userId);
        String vendorEmail = "vendor-" + UUID.randomUUID() + "@example.test";
        Long vendorId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, vendorEmail);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='VENDEDOR'", vendorId);
        jdbc.update("update clients set seller_id=? where id=?", vendorId, clientId);
        mvc.perform(salePost(campaign.getItems().getFirst().getId(), UUID.randomUUID().toString(), "800")
                        .with(user(vendorEmail).roles("VENDEDOR")))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select total_amount from sales where seller_id=?", BigDecimal.class, vendorId)).isEqualByComparingTo("800");
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void rejectsTamperedPriceExpiredCampaignAndWrongProductBeforeStockChanges() throws Exception {
        Campaign campaign = service.create(form(), userId);
        Long itemId = campaign.getItems().getFirst().getId();
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "1"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        var badProduct = new SaleItemInput(productId + 999999L, 1, new BigDecimal("800"), itemId);
        assertThatThrownBy(() -> createSale(badProduct, UUID.randomUUID().toString())).isInstanceOf(InvalidSaleException.class);
        jdbc.update("update campaigns set start_date=?,end_date=? where id=?", LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), campaign.getId());
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "800"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        mvc.perform(get("/sales/new").with(user(email).roles("ADMIN")).param("campaignItemId", itemId.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/campaigns"));
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from sales where seller_id=?", Long.class, userId)).isZero();
    }

    @Test
    void rejectsCreditExtraDiscountAndInsufficientStock() throws Exception {
        Campaign campaign = service.create(form(), userId);
        Long itemId = campaign.getItems().getFirst().getId();
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "800").with(request -> { request.setParameter("paymentType", "CREDIT"); return request; }))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "800").param("discountAmount", "10"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        jdbc.update("update products set stock_available=0 where id=?", productId);
        mvc.perform(salePost(itemId, UUID.randomUUID().toString(), "800"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        assertThat(stock()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sales where seller_id=?", Long.class, userId)).isZero();
    }

    @Test
    void concurrentDuplicateSalesOnlyConsumeStockOnce() throws Exception {
        Campaign campaign = service.create(form(), userId);
        SaleItemInput input = new SaleItemInput(productId, 2, new BigDecimal("800"), campaign.getItems().getFirst().getId());
        String key = UUID.randomUUID().toString();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            Callable<Long> create = () -> { ready.countDown(); start.await(5, TimeUnit.SECONDS); return createSale(input, key).getId(); };
            Future<Long> first = executor.submit(create), second = executor.submit(create);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void protectsManagementAndSellingWithSeparatePermissions() throws Exception {
        Campaign campaign = service.create(form(), userId);
        String readerEmail = "reader-" + UUID.randomUUID() + "@example.test";
        Long readerId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, readerEmail);
        Long roleId = jdbc.queryForObject("insert into roles(name,created_at) values (?,now()) returning id", Long.class, "TEST_" + UUID.randomUUID());
        jdbc.update("insert into role_permissions(role_id,permission_id) select ?,id from permissions where name='CAMPAIGN_READ'", roleId);
        jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", readerId, roleId);
        mvc.perform(get("/campaigns").with(user(readerEmail))).andExpect(status().isOk());
        mvc.perform(get("/campaigns/new").with(user(readerEmail))).andExpect(status().isForbidden());
        mvc.perform(post("/campaigns").with(user(readerEmail)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/campaigns/" + campaign.getId() + "/cancel").with(user(readerEmail)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/sales/new").with(user(readerEmail)).param("campaignItemId", campaign.getItems().getFirst().getId().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/campaigns/" + campaign.getId() + "/cancel").with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void retainsHistoryIfUnsoldProductIsDeleted() throws Exception {
        Campaign campaign = service.create(form(), userId);
        jdbc.update("delete from products where id=?", productId);
        assertThat(service.findById(campaign.getId()).getItems().getFirst().getProductDescription()).isEqualTo("Producto campaña");
        assertThatThrownBy(() -> service.findActiveOffer(campaign.getItems().getFirst().getId())).hasMessageContaining("no está disponible");
        mvc.perform(get("/campaigns/" + campaign.getId()).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Producto no disponible")));
    }

    private CampaignForm form() {
        CampaignForm form = new CampaignForm(); form.setName("Ofertas de temporada"); form.setDescription("Promociones para nuestros clientes");
        form.setStartDate(LocalDate.now()); form.setEndDate(LocalDate.now().plusDays(7)); form.setOperationKey(UUID.randomUUID().toString());
        CampaignForm.Item item = new CampaignForm.Item(); item.setProductId(productId); item.setDiscountMode(DiscountMode.FIXED_PRICE); item.setDiscountValue(new BigDecimal("800"));
        form.setItems(new ArrayList<>(List.of(item))); return form;
    }

    private MockHttpServletRequestBuilder salePost(Long itemId, String key, String price) {
        return post("/sales").with(user(email).roles("ADMIN")).with(csrf()).param("clientId", clientId.toString())
                .param("paymentType", "CASH").param("paymentCollectionMethod", "CASH").param("saleDate", LocalDate.now().toString())
                .param("campaignItemId", itemId.toString()).param("productIds", productId.toString()).param("quantities", "1")
                .param("unitPrices", price).param("idempotencyKey", key);
    }

    private Sale createSale(SaleItemInput input, String key) {
        return saleService.createSale(clients.findById(clientId).orElseThrow(), users.findById(userId).orElseThrow(), PaymentType.CASH,
                PaymentCollectionMethod.CASH, LocalDate.now(), null, null, List.of(), BigDecimal.ZERO, null, null, null, List.of(input), userId, key);
    }

    private int stock() { return jdbc.queryForObject("select stock_available from products where id=?", Integer.class, productId); }
    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target/campaigns-preview"); Files.createDirectories(directory);
        String resources = Path.of("src/main/resources/static").toAbsolutePath().toUri().toString();
        html = html.replace("src=\"/", "src=\"" + resources).replace("href=\"/", "href=\"" + resources);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
