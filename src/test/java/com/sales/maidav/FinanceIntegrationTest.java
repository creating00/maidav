package com.sales.maidav;

import com.sales.maidav.service.finance.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;

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
@Transactional(isolation = Isolation.REPEATABLE_READ)
class FinanceIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MAIDAV_PURCHASES_TEST_URL"));
        registry.add("spring.datasource.username", () -> "purchases_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired FinanceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    private static final LocalDate DAY = LocalDate.of(1984, 6, 15);
    Long userId;
    Long clientId;
    String email;

    @BeforeEach
    void fixtures() {
        email = "finances-" + UUID.randomUUID() + "@example.test";
        userId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, email);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'", userId);
        clientId = jdbc.queryForObject("insert into clients(national_id,first_name,last_name,created_at) values (?,'Cliente','Prueba',now()) returning id", Long.class,
                UUID.randomUUID().toString().replace("-", "").substring(0, 18));
    }

    @Test
    void reconcilesAllSourcesWithoutCountingFinancedSalesOrPaymentAllocationsTwice() throws Exception {
        Long cashSale = sale("CASH", "ACTIVE", "1000", DAY.atStartOfDay(), "CASH");
        sale("CASH", "ACTIVE", "500", DAY.atTime(23, 59, 59, 999999000), "DEBIT");
        sale("CASH", "ACTIVE", "999", DAY.plusDays(1).atStartOfDay(), "CASH");
        sale("CASH", "ACTIVE", "777", DAY.minusDays(1).atTime(23, 59, 59), "CASH");
        Long earlierCredit = sale("CREDIT", "ACTIVE", "10000", DAY.minusMonths(1).atStartOfDay(), null);
        Long account = account(earlierCredit, "OPEN");
        Long payment = payment(account, "300", "450", DAY, false, "TRANSFER");
        payment(account, "-50", "-999", DAY, true, "TRANSFER");
        payment(account, "400", "400", DAY.plusDays(1), false, "CASH");
        sale("CREDIT", "ACTIVE", "9000", DAY.atStartOfDay(), null);
        Long purchase = purchase("200", DAY, "BANK");
        expense("50", DAY, "CASH");

        Long product = product();
        for (int index = 1; index <= 2; index++) {
            jdbc.update("insert into sale_items(sale_id,product_id,quantity,unit_price,line_total,created_at) values (?,?,1,500,500,now())", cashSale, product);
            jdbc.update("insert into purchase_items(purchase_id,product_id,product_code,product_description,quantity,unit_price,line_total,created_at) values (?,?,'TEST','Producto',1,100,100,now())", purchase, product);
            Long installment = jdbc.queryForObject("insert into credit_installments(account_id,installment_number,due_date,amount,status,created_at) values (?,?,?,225,'PENDING',now()) returning id", Long.class, account, index, DAY);
            jdbc.update("insert into credit_payment_allocations(account_id,payment_id,installment_id,collected_amount,impact_amount,verified,created_at) values (?,?,?,150,225,true,now())", account, payment, installment);
        }

        FinanceReport report = service.buildReport(DAY, DAY, 0);
        assertThat(report.getCashSales()).isEqualByComparingTo("1500");
        assertThat(report.getCreditCollections()).isEqualByComparingTo("300");
        assertThat(report.getCreditAdjustments()).isEqualByComparingTo("-50");
        assertThat(report.getIncome()).isEqualByComparingTo("1750");
        assertThat(report.getPayments()).isEqualByComparingTo("250");
        assertThat(report.getDifference()).isEqualByComparingTo("1500");
        assertThat(report.getExclusions().getRegisteredSales()).isEqualByComparingTo("10500");
        assertThat(report.getExclusions().isPresent()).isFalse();
        assertThat(report.getMovements().getTotalElements()).isEqualTo(6);
        assertThat(report.getMovements().getContent().stream().map(FinanceMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(report.getDifference());
        assertThat(report.getMethods().stream().map(FinanceMethodSummary::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(report.getIncome());
        assertThat(report.getMethods().stream().map(FinanceMethodSummary::getPayments).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(report.getPayments());
        assertThat(report.getMethods().stream().map(FinanceMethodSummary::getDifference).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(report.getDifference());
        savePreview("summary", mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("report"))
                .andExpect(content().string(containsString("Diferencia del período"))).andReturn().getResponse().getContentAsString());
    }

    @Test
    void preservesNegativeReversalsInTheirOwnPeriodAndGroupsUnspecifiedMethods() throws Exception {
        Long account = account(sale("CREDIT", "ACTIVE", "1000", DAY.minusDays(10).atStartOfDay(), null), "OPEN");
        payment(account, "600", "600", DAY.minusDays(1), false, null);
        payment(account, "-100", "-500", DAY, true, null);
        FinanceReport report = service.buildReport(DAY, DAY, 0);
        assertThat(report.getIncome()).isEqualByComparingTo("-100");
        assertThat(report.getDifference()).isEqualByComparingTo("-100");
        assertThat(report.getMethods()).singleElement().satisfies(method -> {
            assertThat(method.getMethod()).isEqualTo("UNSPECIFIED");
            assertThat(method.getIncome()).isEqualByComparingTo("-100");
        });
        savePreview("negative", mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void excludesAndReportsVoidedSalesAccountsAndCreditPaymentsLinkedToCashSales() throws Exception {
        sale("CASH", "VOID", "600", DAY.atStartOfDay(), "CASH");
        Long voidSale = sale("CREDIT", "VOID", "800", DAY.atStartOfDay(), null);
        payment(account(voidSale, "OPEN"), "700", "700", DAY, false, "CASH");
        Long voidAccountSale = sale("CREDIT", "ACTIVE", "1200", DAY.atStartOfDay(), null);
        payment(account(voidAccountSale, "VOID"), "200", "200", DAY, false, "CASH");
        Long cashSale = sale("CASH", "ACTIVE", "100", DAY.atStartOfDay(), "CASH");
        payment(account(cashSale, "OPEN"), "100", "100", DAY, false, "CASH");
        FinanceReport report = service.buildReport(DAY, DAY, 0);
        assertThat(report.getIncome()).isEqualByComparingTo("100");
        assertThat(report.getExclusions().getVoidCashCount()).isEqualTo(1);
        assertThat(report.getExclusions().getVoidCashAmount()).isEqualByComparingTo("600");
        assertThat(report.getExclusions().getExcludedCreditCount()).isEqualTo(3);
        assertThat(report.getExclusions().getExcludedCreditAmount()).isEqualByComparingTo("1000");
        assertThat(report.getMovements().getTotalElements()).isEqualTo(1);
        savePreview("excluded", mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Registros excluidos de los cobros")))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void keepsTotalsForEntirePeriodWhenPagingAndClampsInvalidPageNumbers() {
        for (int index = 0; index < 21; index++) expense("1", DAY, "CASH");
        FinanceReport last = service.buildReport(DAY, DAY, Integer.MAX_VALUE);
        assertThat(last.getMovements().getNumber()).isEqualTo(1);
        assertThat(last.getMovements().getContent()).hasSize(1);
        assertThat(last.getExpenses()).isEqualByComparingTo("21");
        assertThat(last.getDifference()).isEqualByComparingTo("-21");
        assertThat(service.buildReport(DAY, DAY, -1).getMovements().getContent()).hasSize(20);
    }

    @Test
    void handlesEmptyPeriodsAndRejectsInvalidRanges() throws Exception {
        LocalDate emptyDay = LocalDate.of(1890, 1, 1);
        FinanceReport empty = service.buildReport(emptyDay, emptyDay, 0);
        assertThat(empty.getDifference()).isZero();
        assertThat(empty.getMovements()).isEmpty();
        assertThat(empty.getMethods()).isEmpty();
        assertThatThrownBy(() -> service.buildReport(DAY.plusDays(1), DAY, 0)).isInstanceOf(InvalidFinancePeriodException.class);
        assertThatThrownBy(() -> service.buildReport(DAY, LocalDate.now().plusDays(1), 0)).isInstanceOf(InvalidFinancePeriodException.class);
        mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("from", DAY.plusDays(1).toString()).param("to", DAY.toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("formError")).andExpect(model().attributeDoesNotExist("report"));
        savePreview("empty", mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("from", emptyDay.toString()).param("to", emptyDay.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void defaultsToCurrentMonthAndUsesSelectedEndMonthWhenOnlyEndIsSupplied() throws Exception {
        mvc.perform(get("/finances").with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(model().attribute("from", LocalDate.now().withDayOfMonth(1)))
                .andExpect(model().attribute("to", LocalDate.now()));
        mvc.perform(get("/finances").with(user(email).roles("ADMIN")).param("to", DAY.toString()))
                .andExpect(status().isOk()).andExpect(model().attribute("from", DAY.withDayOfMonth(1)));
    }

    @Test
    void financePermissionIsIndependentAndDoesNotGrantSourceDetails() throws Exception {
        Long saleId = sale("CASH", "ACTIVE", "20", DAY.atStartOfDay(), "CASH");
        String restrictedEmail = "restricted-" + UUID.randomUUID() + "@example.test";
        Long restrictedId = jdbc.queryForObject("insert into users(email,password) values (?, 'test-unused') returning id", Long.class, restrictedEmail);
        mvc.perform(get("/finances").with(user(restrictedEmail))).andExpect(status().isForbidden());
        mvc.perform(get("/finances")).andExpect(status().is3xxRedirection());
        Long roleId = jdbc.queryForObject("insert into roles(name,created_at) values (?,now()) returning id", Long.class, "TEST_" + UUID.randomUUID());
        jdbc.update("insert into role_permissions(role_id,permission_id) select ?,id from permissions where name='FINANCE_READ'", roleId);
        jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", restrictedId, roleId);
        entityManager.clear(); // SQL fixture updates must be visible to the next security-filter request.
        mvc.perform(get("/finances").with(user(restrictedEmail)).param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Sin permiso de detalle")))
                .andExpect(content().string(not(containsString("href=\"/sales/" + saleId + "\""))))
                .andExpect(content().string(not(containsString("href=\"/expenses\""))));
        mvc.perform(get("/sales/" + saleId).with(user(restrictedEmail))).andExpect(status().isForbidden());
    }

    private Long sale(String type, String status, String amount, LocalDateTime date, String method) {
        return jdbc.queryForObject("insert into sales(client_id,seller_id,payment_type,status,discount_amount,total_amount,sale_date,payment_collection_method,created_at) values (?,?,?,?,0,?,?,?,now()) returning id",
                Long.class, clientId, userId, type, status, new BigDecimal(amount), date, method);
    }

    private Long account(Long saleId, String status) {
        return jdbc.queryForObject("insert into credit_accounts(sale_id,client_id,total_amount,balance,weeks_count,start_date,status,created_at) values (?,?,10000,10000,2,?,?,now()) returning id",
                Long.class, saleId, clientId, DAY.minusMonths(1), status);
    }

    private Long payment(Long accountId, String amount, String impact, LocalDate date, boolean reversal, String method) {
        return jdbc.queryForObject("insert into credit_payments(account_id,amount,impact_amount,paid_at,reversal,payment_method,created_at) values (?,?,?,?,?,?,now()) returning id",
                Long.class, accountId, new BigDecimal(amount), new BigDecimal(impact), date, reversal, method);
    }

    private Long purchase(String amount, LocalDate date, String method) {
        return jdbc.queryForObject("insert into purchases(purchase_date,registered_by_id,payment_method,total_amount) values (?,?,?,?) returning id",
                Long.class, date, userId, method, new BigDecimal(amount));
    }

    private void expense(String amount, LocalDate date, String method) {
        jdbc.update("insert into expenses(expense_date,registered_by_id,payment_method,amount,category,description) values (?,?,?,?,'OTHER','Gasto de prueba')",
                date, userId, method, new BigDecimal(amount));
    }

    private Long product() {
        Long provider = jdbc.queryForObject("insert into providers(name,created_at) values ('Proveedor de prueba',now()) returning id", Long.class);
        return jdbc.queryForObject("insert into products(provider_id,product_code,description,cost,price_wholesale_net,price_retail_net,vat_rate,price_wholesale,price_retail,stock_available,stock_min,stock_max,created_at) values (?,?,'Producto',20,30,40,0,30,40,10,0,100,now()) returning id",
                Long.class, provider, UUID.randomUUID().toString());
    }

    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target/finances-preview");
        Files.createDirectories(directory);
        String resources = Path.of("src/main/resources/static").toAbsolutePath().toUri().toString();
        html = html.replace("src=\"/", "src=\"" + resources).replace("href=\"/", "href=\"" + resources);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
