package com.sales.maidav;

import com.sales.maidav.model.expense.*;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.expense.*;
import com.sales.maidav.web.dto.ExpenseForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "MAIDAV_PURCHASES_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/maidav_purchases_test")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("purchases-test")
class ExpenseIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MAIDAV_PURCHASES_TEST_URL"));
        registry.add("spring.datasource.username", () -> "purchases_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired ExpenseService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long userId;
    String email;
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void fixtures() {
        email = "expenses-" + UUID.randomUUID() + "@example.test";
        userId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, email);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'", userId);
    }

    @Test
    void createsPaidExpenseWithoutReceiptAndCombinesInclusiveFilters() throws Exception {
        ExpenseForm form = form();
        Expense expense = service.create(form, userId, null);
        assertThat(expense.getAmount()).isEqualByComparingTo("345.67");
        assertThat(expense.getReceiptName()).isNull();
        assertThat(service.findPage(form.getExpenseDate(), form.getExpenseDate(), form.getCategory(), form.getAmount(), form.getAmount(), 0)
                .getContent()).extracting(Expense::getId).contains(expense.getId());
        assertThat(service.findPage(null, null, ExpenseCategory.RENT, null, null, 0).getContent())
                .extracting(Expense::getId).doesNotContain(expense.getId());
        assertThat(service.findPage(null, null, null, new BigDecimal("346"), null, 0).getContent())
                .extracting(Expense::getId).doesNotContain(expense.getId());
        mvc.perform(get("/expenses/" + expense.getId() + "/receipt").with(user(email).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void postsReceiptAndRendersPagesAndDownloadsOnlyThroughProtectedRoute() throws Exception {
        String filename = "factura-" + UUID.randomUUID() + ".pdf";
        var response = mvc.perform(multipart("/expenses").file(new MockMultipartFile("receipt", "../" + filename, "text/html", PDF))
                        .with(user(email).roles("ADMIN")).with(csrf())
                        .param("expenseDate", LocalDate.now().toString()).param("category", "SERVICES")
                        .param("description", "Electricidad del local").param("amount", "15000.25")
                        .param("paymentMethod", "TRANSFER").param("operationKey", UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        String location = response.getRedirectedUrl();
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        Expense expense = service.findById(id);
        assertThat(expense.getReceiptName()).isEqualTo(filename);
        assertThat(expense.getReceiptContentType()).isEqualTo("application/pdf");
        assertThat(expense.getReceiptSize()).isEqualTo(PDF.length);
        mvc.perform(get(location + "/receipt").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        savePreview("form", mvc.perform(get("/expenses/new").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        savePreview("index", mvc.perform(get("/expenses").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        savePreview("detail", mvc.perform(get(location).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Descargar comprobante")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/uploads/" + filename)).andExpect(status().isNotFound());
    }

    @Test
    void deniesUnauthorizedRoutesAndRequiresCsrf() throws Exception {
        Expense expense = service.create(form(), userId, receipt());
        String restricted = "restricted-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users(email,password) values (?, 'test-unused')", restricted);
        for (String path : new String[]{"/expenses", "/expenses/new", "/expenses/" + expense.getId(), "/expenses/" + expense.getId() + "/receipt"}) {
            mvc.perform(get(path).with(user(restricted))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        }
        mvc.perform(multipart("/expenses").with(user(restricted)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/expenses").with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000"})
    void rejectsInvalidAmountsWithoutPersistingExpenseOrKey(String amount) {
        ExpenseForm form = form();
        form.setAmount(new BigDecimal(amount));
        assertThatThrownBy(() -> service.create(form, userId, null)).isInstanceOf(InvalidExpenseException.class);
        assertNoRecords();
    }

    @Test
    void rejectsMissingDescriptionAndFutureDate() {
        ExpenseForm form = form();
        form.setDescription("   ");
        assertThatThrownBy(() -> service.create(form, userId, null)).hasMessageContaining("descripción");
        form.setDescription("Servicio");
        form.setExpenseDate(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> service.create(form, userId, null)).hasMessageContaining("fecha");
        assertNoRecords();
    }

    @Test
    void rejectsOversizedAndDisguisedFilesWithoutPersistingAnyRecords() {
        var oversized = new MockMultipartFile("receipt", "grande.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> service.create(form(), userId, oversized)).hasMessageContaining("5 MB");
        var disguised = new MockMultipartFile("receipt", "falso.pdf", "application/pdf", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.create(form(), userId, disguised)).hasMessageContaining("PDF, JPG o PNG");
        assertNoRecords();
    }

    @Test
    void derivesDownloadExtensionAndTypeFromContentInsteadOfUploadedName() {
        var file = new MockMultipartFile("receipt", "../recibo.bat", "application/octet-stream", PDF);
        Expense expense = service.create(form(), userId, file);
        assertThat(expense.getReceiptName()).isEqualTo("recibo.pdf");
        assertThat(expense.getReceiptContentType()).isEqualTo("application/pdf");
        assertThat(service.findReceipt(expense.getId()).getContent()).isEqualTo(PDF);
    }

    @Test
    void repeatedConcurrentRequestsPersistOneExpenseAndOneReceipt() throws Exception {
        ExpenseForm form = form();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Long> create = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.create(form, userId, receipt()).getId();
            };
            Future<Long> first = executor.submit(create);
            Future<Long> second = executor.submit(create);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Long id = first.get(15, TimeUnit.SECONDS);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(service.findReceipt(id).getContent()).isEqualTo(PDF);
        }
        assertThat(jdbc.queryForObject("select count(*) from expenses where registered_by_id=?", Long.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from expense_receipts r join expenses e on e.id=r.expense_id where e.registered_by_id=?", Long.class, userId)).isEqualTo(1);
    }

    @Test
    void preservesInputWhenBindingFailsAndShowsInvalidFilterRanges() throws Exception {
        mvc.perform(multipart("/expenses").file(receipt()).with(user(email).roles("ADMIN")).with(csrf())
                        .param("operationKey", UUID.randomUUID().toString()).param("expenseDate", LocalDate.now().toString())
                        .param("category", "FUEL").param("description", "Conservar descripción")
                        .param("amount", "importe-invalido").param("paymentMethod", "CASH"))
                .andExpect(status().isOk()).andExpect(model().attribute("receiptMustReselect", true))
                .andExpect(model().attribute("expenseForm", hasProperty("description", equalTo("Conservar descripción"))));
        mvc.perform(get("/expenses").with(user(email).roles("ADMIN")).param("minAmount", "100").param("maxAmount", "50"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        mvc.perform(get("/expenses").with(user(email).roles("ADMIN")).param("from", "2026-11-19").param("to", "2026-11-18"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError"));
        assertNoRecords();
    }

    @Test
    void creationOnlyPermissionShowsExpensesAndCompletesWithoutReadPermission() throws Exception {
        jdbc.update("delete from user_roles where user_id=?", userId);
        String role = "TEST_" + UUID.randomUUID();
        Long roleId = jdbc.queryForObject("insert into roles(name,created_at) values (?,now()) returning id", Long.class, role);
        jdbc.update("insert into role_permissions(role_id,permission_id) select ?,id from permissions where name='EXPENSE_CREATE'", roleId);
        jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", userId, roleId);
        mvc.perform(get("/expenses/new").with(user(email)))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("href=\"/purchases\""))));
        mvc.perform(multipart("/expenses").with(user(email)).with(csrf())
                        .param("operationKey", UUID.randomUUID().toString()).param("expenseDate", LocalDate.now().toString())
                        .param("category", "OTHER").param("description", "Gasto autorizado").param("amount", "10.50").param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/expenses/new"));
        mvc.perform(get("/expenses").with(user(email))).andExpect(status().isForbidden());
    }

    private ExpenseForm form() {
        var form = new ExpenseForm();
        form.setExpenseDate(LocalDate.now());
        form.setCategory(ExpenseCategory.SERVICES);
        form.setDescription("Servicio del local");
        form.setAmount(new BigDecimal("345.67"));
        form.setPaymentMethod(PaymentCollectionMethod.TRANSFER);
        form.setOperationKey(UUID.randomUUID().toString());
        return form;
    }

    private MockMultipartFile receipt() {
        return new MockMultipartFile("receipt", "factura.pdf", "application/pdf", PDF);
    }

    private void assertNoRecords() {
        assertThat(jdbc.queryForObject("select count(*) from expenses where registered_by_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from expense_idempotency_keys where user_id=?", Long.class, userId)).isZero();
    }

    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target/expenses-preview");
        Files.createDirectories(directory);
        String resources = Path.of("src/main/resources/static").toAbsolutePath().toUri().toString();
        html = html.replace("src=\"/", "src=\"" + resources).replace("href=\"/", "href=\"" + resources);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
