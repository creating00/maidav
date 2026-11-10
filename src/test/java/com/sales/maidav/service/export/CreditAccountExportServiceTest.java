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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

class CreditAccountExportServiceTest {
    @Test
    void exportsOneSheetWithOneRowPerLinkedPayment() throws Exception {
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
        client.setPhone("01123456789");
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
        valid.setAmount(new BigDecimal("200.00"));
        valid.setImpactAmount(new BigDecimal("200.00"));
        valid.setPaymentMethod(PaymentCollectionMethod.TRANSFER);
        ReflectionTestUtils.setField(valid, "createdAt", LocalDateTime.of(2026, 9, 29, 10, 56));
        CreditPayment debitPayment = payment(5L, account, "collector@example.com", false, null);
        debitPayment.setPaidAt(LocalDate.now());
        debitPayment.setAmount(new BigDecimal("300.00"));
        debitPayment.setImpactAmount(new BigDecimal("300.00"));
        debitPayment.setPaymentMethod(PaymentCollectionMethod.DEBIT);
        CreditPayment oldPayment = payment(6L, account, "admin@example.com", false, null);
        oldPayment.setPaidAt(LocalDate.now().plusDays(1));
        oldPayment.setAmount(new BigDecimal("50.00"));
        oldPayment.setImpactAmount(new BigDecimal("50.00"));
        oldPayment.setPaymentMethod(PaymentCollectionMethod.CASH);
        when(payments.findByAccount_IdIn(List.of(20L)))
                .thenReturn(List.of(valid, debitPayment, oldPayment, reversal));
        when(allocations.findByAccount_IdIn(List.of(20L))).thenReturn(List.of(
                allocation(account, paidInstallment, valid, "200.00", "200.00", true),
                allocation(account, paidInstallment, debitPayment, "300.00", "300.00", true)
        ));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheet("Cuotas");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 15);
            assertThat(java.util.stream.IntStream.range(0, 15)
                    .mapToObj(column -> sheet.getRow(0).getCell(column).getStringCellValue()).toList())
                    .containsExactly("id_pago", "numero_cuenta", "cliente", "vendedor", "producto",
                            "costo_producto", "nro_cuota", "fecha_vencimiento", "monto_cuota_efectivo",
                            "monto_cuota_transferencia", "estado_cuota", "fecha_pago_real",
                            "monto_pagado", "metodo_pago", "telefono");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("C-000020");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Mesa | Silla");
            assertThat(sheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(4000.0);
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).isEqualTo("Pagado");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(200.0);
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("Transferencia");
            assertThat(sheet.getRow(1).getCell(14).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(14).getStringCellValue()).isEqualTo("01123456789");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("5");
            assertThat(sheet.getRow(2).getCell(6).getNumericCellValue()).isEqualTo(1.0);
            assertThat(sheet.getRow(2).getCell(12).getNumericCellValue()).isEqualTo(300.0);
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("Débito");
            assertThat(sheet.getRow(2).getCell(14).getStringCellValue()).isEqualTo("01123456789");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(3).getCell(10).getStringCellValue()).isEqualTo("Atrasada");
            assertThat(sheet.getRow(3).getCell(12).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(3).getCell(14).getStringCellValue()).isEqualTo("01123456789");
        }
    }

    @Test
    void repeatsInstallmentForEachPaymentExactlyAsCustomerExample() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditPaymentRepository payments = mock(CreditPaymentRepository.class);
        CreditInstallmentRepository installments = mock(CreditInstallmentRepository.class);
        CreditPaymentAllocationRepository allocations = mock(CreditPaymentAllocationRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, payments, installments,
                allocations, mock(UserRepository.class), settingsService, new QuoteCalculator(),
                new ExportDocumentService());

        Client client = new Client();
        client.setFirstName("jose");
        client.setLastName("juarez");
        User seller = new User();
        seller.setEmail("admin@miadav.com");
        Sale sale = new Sale();
        sale.setId(12L);
        sale.setSeller(seller);
        CreditAccount account = new CreditAccount();
        account.setId(12L);
        account.setAccountNumber("C-00012");
        account.setClient(client);
        account.setSale(sale);
        account.setPaymentFrequency(PaymentFrequency.WEEKLY);

        CreditInstallment first = exampleInstallment(account, 1L, 1, LocalDate.of(2026, 10, 10));
        CreditInstallment second = exampleInstallment(account, 2L, 2, LocalDate.of(2026, 11, 10));
        CreditPayment payment119 = payment(119L, account, seller.getEmail(), false, null);
        payment119.setPaidAt(LocalDate.of(2026, 10, 15));
        payment119.setAmount(new BigDecimal("15"));
        payment119.setPaymentMethod(PaymentCollectionMethod.BANK);
        payment119.setAllocationSummary("Cuota #1: se aplican $ 15,00 y queda saldada (valor financiado)");
        CreditPayment payment120 = payment(120L, account, seller.getEmail(), false, null);
        payment120.setPaidAt(LocalDate.of(2026, 11, 15));
        payment120.setAmount(new BigDecimal("10"));
        payment120.setPaymentMethod(PaymentCollectionMethod.CASH);
        payment120.setAllocationSummary("Cuota #2: se aplican $ 10,00 y quedan $ 5,00 pendientes (valor financiado)");
        CreditPayment payment121 = payment(121L, account, seller.getEmail(), false, null);
        payment121.setPaidAt(LocalDate.of(2026, 11, 25));
        payment121.setAmount(new BigDecimal("5"));
        payment121.setPaymentMethod(PaymentCollectionMethod.CASH);
        payment121.setAllocationSummary("Cuota #2: se aplican $ 5,00 y queda saldada (valor financiado)");

        when(settingsService.getSettings()).thenReturn(new CompanySettings());
        when(items.findBySale_IdInOrderBySale_IdAscIdAsc(List.of(12L)))
                .thenReturn(List.of(item(sale, "VENT", "ventilador", "100", 1)));
        when(installments.findByAccount_IdInOrderByAccount_IdAscInstallmentNumberAsc(List.of(12L)))
                .thenReturn(List.of(first, second));
        when(payments.findByAccount_IdIn(List.of(12L)))
                .thenReturn(List.of(payment119, payment120, payment121));
        when(allocations.findByAccount_IdIn(List.of(12L))).thenReturn(List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("119");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("C-00012");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("jose juarez");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("admin@miadav.com");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("ventilador");
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(100);
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("10/10/2026");
            assertThat(sheet.getRow(1).getCell(8).getNumericCellValue()).isEqualTo(10);
            assertThat(sheet.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(15);
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).isEqualTo("Pagado");
            assertThat(sheet.getRow(1).getCell(11).getStringCellValue()).isEqualTo("15/10/2026");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(15);
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("Débito/Transferencia");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("120");
            assertThat(sheet.getRow(2).getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("10/11/2026");
            assertThat(sheet.getRow(2).getCell(10).getStringCellValue()).isEqualTo("Pagado");
            assertThat(sheet.getRow(2).getCell(11).getStringCellValue()).isEqualTo("15/11/2026");
            assertThat(sheet.getRow(2).getCell(12).getNumericCellValue()).isEqualTo(10);
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("Efectivo");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("121");
            assertThat(sheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(3).getCell(11).getStringCellValue()).isEqualTo("25/11/2026");
            assertThat(sheet.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(5);
            assertThat(sheet.getRow(3).getCell(13).getStringCellValue()).isEqualTo("Efectivo");
        }
    }

    private CreditInstallment exampleInstallment(CreditAccount account, Long id, int number, LocalDate dueDate) {
        CreditInstallment installment = new CreditInstallment();
        installment.setId(id);
        installment.setAccount(account);
        installment.setInstallmentNumber(number);
        installment.setDueDate(dueDate);
        installment.setCashAmount(new BigDecimal("10"));
        installment.setAmount(new BigDecimal("15"));
        installment.setPaidAmount(new BigDecimal("15"));
        installment.setStatus(InstallmentStatus.PAID);
        return installment;
    }

    @Test
    void historicalPaymentAmountsRequireAnExactSplitAcrossMultipleInstallments() {
        CreditAccountExportService service = new CreditAccountExportService(
                mock(SaleItemRepository.class), mock(CreditPaymentRepository.class),
                mock(CreditInstallmentRepository.class), mock(CreditPaymentAllocationRepository.class),
                mock(UserRepository.class), mock(CompanySettingsService.class),
                new QuoteCalculator(), new ExportDocumentService());
        CreditPayment split = new CreditPayment();
        split.setAmount(new BigDecimal("35500.00"));
        split.setAllocationSummary("Cuota #1: se aplican $ 35.450,00 y queda saldada (valor financiado)"
                + " | Cuota #2: recibe $ 50,00 por saldo a favor y quedan $ 35.400,00 pendientes (valor financiado)");
        assertThat((Map<Integer, BigDecimal>) ReflectionTestUtils.invokeMethod(service, "historicalAmounts", split))
                .containsExactlyInAnyOrderEntriesOf(Map.of(1, new BigDecimal("35450.00"),
                        2, new BigDecimal("50.00")));

        split.setAmount(new BigDecimal("36000.00"));
        assertThat((Map<Integer, BigDecimal>) ReflectionTestUtils.invokeMethod(service, "historicalAmounts", split))
                .isEmpty();

        CreditPayment single = new CreditPayment();
        single.setAmount(new BigDecimal("50000.00"));
        single.setAllocationSummary("Cuota #8: se aplican $ 35.450,00 y queda saldada (valor financiado)");
        assertThat((Map<Integer, BigDecimal>) ReflectionTestUtils.invokeMethod(service, "historicalAmounts", single))
                .containsEntry(8, new BigDecimal("50000.00"));
    }

    @Test
    void partialReversalKeepsPaymentOnOtherPaidInstallments() throws Exception {
        SaleItemRepository items = mock(SaleItemRepository.class);
        CreditPaymentRepository payments = mock(CreditPaymentRepository.class);
        CreditInstallmentRepository installments = mock(CreditInstallmentRepository.class);
        CreditPaymentAllocationRepository allocations = mock(CreditPaymentAllocationRepository.class);
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CreditAccountExportService service = new CreditAccountExportService(items, payments, installments,
                allocations, mock(UserRepository.class), settingsService, new QuoteCalculator(),
                new ExportDocumentService());
        Sale sale = new Sale();
        sale.setId(30L);
        CreditAccount account = new CreditAccount();
        account.setId(30L);
        account.setSale(sale);
        account.setAccountNumber("C-000030");
        account.setPaymentFrequency(PaymentFrequency.WEEKLY);
        CreditInstallment voidedSecond = exampleInstallment(account, 813L, 2, LocalDate.of(2026, 9, 24));
        voidedSecond.setVoided(true);
        voidedSecond.setStatus(InstallmentStatus.VOID);
        CreditInstallment second = exampleInstallment(account, 816L, 2, LocalDate.of(2026, 9, 24));
        second.setPaidAmount(BigDecimal.ZERO);
        second.setStatus(InstallmentStatus.PENDING);
        CreditInstallment third = exampleInstallment(account, 814L, 3, LocalDate.of(2026, 10, 24));
        CreditInstallment fourth = exampleInstallment(account, 815L, 4, LocalDate.of(2026, 11, 24));
        CreditPayment original = payment(349L, account, "admin@example.com", false, null);
        original.setAmount(new BigDecimal("300"));
        original.setPaidAt(LocalDate.of(2026, 8, 25));
        original.setPaymentMethod(PaymentCollectionMethod.CASH);
        original.setAllocationSummary("Cuota #2: se aplican $ 100,00 y queda saldada (valor contado)"
                + " | Cuota #3: se aplican $ 100,00 y queda saldada (valor contado)"
                + " | Cuota #4: se aplican $ 100,00 y queda saldada (valor contado)");
        CreditPayment reversal = payment(350L, account, "admin@example.com", true, 349L);
        reversal.setTargetInstallmentId(816L);
        reversal.setAmount(new BigDecimal("-100"));
        when(settingsService.getSettings()).thenReturn(new CompanySettings());
        when(installments.findByAccount_IdInOrderByAccount_IdAscInstallmentNumberAsc(List.of(30L)))
                .thenReturn(List.of(voidedSecond, second, third, fourth));
        when(payments.findByAccount_IdIn(List.of(30L))).thenReturn(List.of(original, reversal));
        when(allocations.findByAccount_IdIn(List.of(30L))).thenReturn(List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(account))))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("349");
            assertThat(sheet.getRow(2).getCell(6).getNumericCellValue()).isEqualTo(3);
            assertThat(sheet.getRow(2).getCell(12).getNumericCellValue()).isEqualTo(100);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("349");
            assertThat(sheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(4);
            assertThat(sheet.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(100);
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
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("11");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("Atrasada");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo(
                    LocalDate.now().minusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            assertThat(row.getCell(12).getNumericCellValue()).isEqualTo(100.0);
            assertThat(row.getCell(13).getStringCellValue()).isEqualTo("Efectivo");
            var secondRow = workbook.getSheet("Cuotas").getRow(2);
            assertThat(secondRow.getCell(0).getStringCellValue()).isEqualTo("12");
            assertThat(secondRow.getCell(6).getNumericCellValue()).isEqualTo(3.0);
            assertThat(secondRow.getCell(12).getNumericCellValue()).isEqualTo(150.0);
            assertThat(secondRow.getCell(13).getStringCellValue()).isEqualTo("Transferencia");
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
