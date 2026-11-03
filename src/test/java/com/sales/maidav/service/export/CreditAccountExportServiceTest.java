package com.sales.maidav.service.export;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.*;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.Role;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentAllocationRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.quote.QuoteCalculator;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditAccountExportServiceTest {
    @Test
    void exportsOneRowPerInstallmentWithPaymentIdsAndExampleColumns() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditPaymentRepository payments = mock(CreditPaymentRepository.class);
        CreditInstallmentRepository installments = mock(CreditInstallmentRepository.class);
        CreditPaymentAllocationRepository allocations = mock(CreditPaymentAllocationRepository.class);
        UserRepository users = mock(UserRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, payments, installments, allocations, users,
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

        CreditInstallment paidInstallment = new CreditInstallment();
        paidInstallment.setId(31L);
        paidInstallment.setAccount(account);
        paidInstallment.setInstallmentNumber(1);
        paidInstallment.setDueDate(LocalDate.now().minusDays(2));
        paidInstallment.setAmount(new BigDecimal("500.00"));
        paidInstallment.setCashAmount(new BigDecimal("400.00"));
        paidInstallment.setPaidAmount(new BigDecimal("500.00"));
        paidInstallment.setStatus(InstallmentStatus.PAID);
        CreditInstallment partialInstallment = new CreditInstallment();
        partialInstallment.setId(32L);
        partialInstallment.setAccount(account);
        partialInstallment.setInstallmentNumber(2);
        partialInstallment.setDueDate(LocalDate.now().minusDays(1));
        partialInstallment.setAmount(new BigDecimal("500.00"));
        partialInstallment.setPaidAmount(new BigDecimal("100.00"));
        partialInstallment.setStatus(InstallmentStatus.PARTIAL);
        when(installments.findByAccount_IdInOrderByAccount_IdAscInstallmentNumberAsc(List.of(20L)))
                .thenReturn(List.of(paidInstallment, partialInstallment));
        valid.setPaidAt(LocalDate.now().minusDays(1));
        valid.setPaymentMethod(PaymentCollectionMethod.TRANSFER);
        CreditPayment debitPayment = payment(5L, account, "collector@example.com", false, null);
        debitPayment.setPaidAt(LocalDate.now());
        debitPayment.setPaymentMethod(PaymentCollectionMethod.DEBIT);
        when(allocations.findByAccount_IdIn(List.of(20L))).thenReturn(List.of(
                allocation(account, paidInstallment, valid, "200.00", "200.00", true),
                allocation(account, paidInstallment, debitPayment, "300.00", "300.00", true)
        ));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheet("Cuotas");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 14);
            assertThat(java.util.stream.IntStream.range(0, 14)
                    .mapToObj(column -> sheet.getRow(0).getCell(column).getStringCellValue()).toList())
                    .containsExactly("id_pago", "numero_cuenta", "cliente", "vendedor", "producto",
                            "costo_producto", "nro_cuota", "fecha_vencimiento", "monto_cuota_efectivo",
                            "monto_cuota_transferencia", "estado_cuota", "fecha_pago_real",
                            "monto_pagado", "metodo_pago");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("1, 5");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("C-000020");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Mesa | Silla");
            assertThat(sheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(4000.0);
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).isEqualTo("Pagado");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(500.0);
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("Transferencia + Débito");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(2).getCell(10).getStringCellValue()).isEqualTo("Atrasada");
            assertThat(sheet.getRow(2).getCell(12).getStringCellValue()).isBlank();
        }
    }

    @Test
    void dailyInstallmentShowsFinancedAmountForCashAndTransfer() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditPaymentRepository payments = mock(CreditPaymentRepository.class);
        CreditInstallmentRepository installments = mock(CreditInstallmentRepository.class);
        CreditPaymentAllocationRepository allocations = mock(CreditPaymentAllocationRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, payments, installments,
                allocations, mock(UserRepository.class), settingsService, new QuoteCalculator(),
                new ExportDocumentService());
        Sale sale = new Sale();
        sale.setId(11L);
        CreditAccount account = new CreditAccount();
        account.setId(21L);
        account.setSale(sale);
        account.setPaymentFrequency(PaymentFrequency.DAILY);
        CreditInstallment installment = new CreditInstallment();
        installment.setId(31L);
        installment.setAccount(account);
        installment.setInstallmentNumber(1);
        installment.setDueDate(LocalDate.now().plusDays(1));
        installment.setAmount(new BigDecimal("750.00"));
        installment.setPaidAmount(BigDecimal.ZERO);
        installment.setStatus(InstallmentStatus.PENDING);
        when(settingsService.getSettings()).thenReturn(new CompanySettings());
        when(installments.findByAccount_IdInOrderByAccount_IdAscInstallmentNumberAsc(List.of(21L)))
                .thenReturn(List.of(installment));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            var row = workbook.getSheet("Cuotas").getRow(1);
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(750.0);
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(750.0);
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("Pendiente");
            assertThat(row.getCell(12).getStringCellValue()).isBlank();
        }
    }

    @Test
    void partialPaymentsRemainOverdueAndShowActualCollections() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditInstallmentRepository installments = mock(CreditInstallmentRepository.class);
        CreditPaymentAllocationRepository allocations = mock(CreditPaymentAllocationRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, mock(CreditPaymentRepository.class),
                installments, allocations, mock(UserRepository.class), settingsService, new QuoteCalculator(),
                new ExportDocumentService());
        Sale sale = new Sale();
        sale.setId(12L);
        CreditAccount account = new CreditAccount();
        account.setId(22L);
        account.setSale(sale);
        account.setPaymentFrequency(PaymentFrequency.DAILY);
        CreditInstallment installment = new CreditInstallment();
        installment.setId(33L);
        installment.setAccount(account);
        installment.setInstallmentNumber(3);
        installment.setDueDate(LocalDate.now().minusDays(3));
        installment.setAmount(new BigDecimal("750.00"));
        installment.setPaidAmount(new BigDecimal("250.00"));
        installment.setStatus(InstallmentStatus.PARTIAL);
        CreditPayment first = payment(11L, account, "collector@example.com", false, null);
        first.setPaidAt(LocalDate.now().minusDays(2));
        first.setPaymentMethod(PaymentCollectionMethod.CASH);
        CreditPayment second = payment(12L, account, "collector@example.com", false, null);
        second.setPaidAt(LocalDate.now().minusDays(1));
        second.setPaymentMethod(PaymentCollectionMethod.TRANSFER);
        when(settingsService.getSettings()).thenReturn(new CompanySettings());
        when(installments.findByAccount_IdInOrderByAccount_IdAscInstallmentNumberAsc(List.of(22L)))
                .thenReturn(List.of(installment));
        when(allocations.findByAccount_IdIn(List.of(22L))).thenReturn(List.of(
                allocation(account, installment, first, "100.00", "100.00", true),
                allocation(account, installment, second, "150.00", "150.00", true)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            var row = workbook.getSheet("Cuotas").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("11, 12");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("Atrasada");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo(
                    LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            assertThat(row.getCell(12).getNumericCellValue()).isEqualTo(250.0);
            assertThat(row.getCell(13).getStringCellValue()).isEqualTo("Efectivo + Transferencia");
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

    private CreditPaymentAllocation allocation(CreditAccount account, CreditInstallment installment,
                                               CreditPayment payment, String collected, String impact,
                                               boolean verified) {
        CreditPaymentAllocation allocation = new CreditPaymentAllocation();
        allocation.setAccount(account);
        allocation.setInstallment(installment);
        allocation.setPayment(payment);
        allocation.setCollectedAmount(new BigDecimal(collected));
        allocation.setImpactAmount(new BigDecimal(impact));
        allocation.setVerified(verified);
        return allocation;
    }
}
