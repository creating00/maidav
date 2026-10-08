package com.sales.maidav.service.export;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.*;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.Role;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.quote.QuoteCalculator;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditAccountExportServiceTest {
    @Test
    void exportsOneRowPerProductWithCurrentInstallmentPricesAndEligibleCollectors() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditPaymentRepository payments = mock(CreditPaymentRepository.class);
        UserRepository users = mock(UserRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, payments, users,
                settingsService, new QuoteCalculator(), new ExportDocumentService());

        Client client = new Client();
        client.setNationalId("12345678");
        client.setFirstName("Ana");
        Sale sale = new Sale();
        sale.setId(10L);
        User seller = new User();
        seller.setEmail("seller@example.com");
        sale.setSeller(seller);
        CreditAccount account = new CreditAccount();
        account.setId(20L);
        account.setSale(sale);
        account.setClient(client);
        account.setAccountNumber("C-000020");
        account.setTotalAmount(new BigDecimal("12000.00"));
        account.setBalance(new BigDecimal("3000.00"));
        account.setPaymentFrequency(PaymentFrequency.WEEKLY);
        account.setWeeksCount(13);

        SaleItem first = item(sale, "P1", "Mesa", "1000.00", 2);
        SaleItem second = item(sale, "P2", "Silla", "2000.00", 1);
        when(items.findBySale_IdInOrderBySale_IdAscIdAsc(List.of(10L))).thenReturn(List.of(first, second));

        CreditPayment valid = payment(1L, account, "collector@example.com", false, null);
        CreditPayment reversed = payment(2L, account, "admin@example.com", false, null);
        CreditPayment reversal = payment(3L, account, "admin@example.com", true, 2L);
        CreditPayment sellerPayment = payment(4L, account, "seller@example.com", false, null);
        when(payments.findByAccount_IdIn(List.of(20L))).thenReturn(List.of(valid, reversed, reversal, sellerPayment));
        User collector = new User();
        collector.setEmail("collector@example.com");
        collector.setRoles(Set.of(new Role("COBRADOR")));
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setRoles(Set.of(new Role("ADMIN")));
        seller.setRoles(Set.of(new Role("VENDEDOR")));
        when(users.findByEmailIn(anyList())).thenReturn(List.of(collector, admin, seller));

        CompanySettings settings = new CompanySettings();
        settings.setCalcMonthlyLongMinCost(new BigDecimal("1500.00"));
        when(settingsService.getSettings()).thenReturn(settings);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(1).getCell(21).getStringCellValue()).isEqualTo("precio_13_semanas");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("C-000020");
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("collector@example.com");
            assertThat(sheet.getRow(2).getCell(14).getStringCellValue()).isEqualTo("13 semanas");
            assertThat(sheet.getRow(2).getCell(15).getStringCellValue()).isEqualTo("P1 - Mesa");
            assertThat(sheet.getRow(2).getCell(17).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(2).getCell(17).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(sheet.getRow(2).getCell(18).getNumericCellValue()).isEqualTo(1300.0);
            assertThat(sheet.getRow(2).getCell(19).getNumericCellValue()).isEqualTo(1500.0);
            assertThat(sheet.getRow(2).getCell(21).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(2).getCell(21).getNumericCellValue()).isEqualTo(200.0);
            assertThat(sheet.getRow(2).getCell(23).getStringCellValue()).isEqualTo("No aplica");
            assertThat(sheet.getRow(3).getCell(15).getStringCellValue()).isEqualTo("P2 - Silla");
            assertThat(sheet.getRow(3).getCell(23).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    private SaleItem item(Sale sale, String code, String name, String cost, int quantity) {
        Product product = new Product();
        product.setProductCode(code);
        product.setDescription(name);
        product.setCost(new BigDecimal(cost));
        product.setVatRate(BigDecimal.ZERO);
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private CreditPayment payment(Long id, CreditAccount account, String email, boolean reversal, Long originalId) {
        CreditPayment payment = new CreditPayment();
        payment.setId(id);
        payment.setAccount(account);
        payment.setRegisteredBy(email);
        payment.setReversal(reversal);
        payment.setReversalOfPaymentId(originalId);
        return payment;
    }
}
