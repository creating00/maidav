package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.CreditPayment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleStatus;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditAccountServiceImplTest {

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @Mock
    private CreditInstallmentRepository creditInstallmentRepository;

    @Mock
    private CreditPaymentRepository creditPaymentRepository;

    @Mock
    private CompanySettingsService companySettingsService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreditAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paymentInDateUsesCashValueAndAppliesExcessOnNextInstallment() {
        CreditAccount account = account(1L, PaymentFrequency.WEEKLY, "2400.00");
        CreditInstallment firstInstallment = installment(account, 11L, 1, "1200.00", LocalDate.now());
        CreditInstallment secondInstallment = installment(account, 12L, 2, "1200.00", LocalDate.now().plusWeeks(1));
        List<CreditInstallment> installments = List.of(firstInstallment, secondInstallment);

        mockAccount(account, installments, new BigDecimal("1.20"));

        service.registerPayment(1L, new BigDecimal("1500.00"), null, "tester", PaymentCollectionMethod.CASH);

        assertThat(firstInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstInstallment.getPaidAmount()).isEqualByComparingTo("1200.00");
        assertThat(secondInstallment.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("500.00");
        assertThat(account.getBalance()).isEqualByComparingTo("700.00");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.OPEN);

        service.registerPayment(1L, new BigDecimal("500.00"), null, "tester", PaymentCollectionMethod.CASH);

        assertThat(secondInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("1200.00");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);

        ArgumentCaptor<CreditPayment> paymentCaptor = ArgumentCaptor.forClass(CreditPayment.class);
        verify(creditPaymentRepository, times(2)).save(paymentCaptor.capture());
        List<CreditPayment> savedPayments = paymentCaptor.getAllValues();

        assertThat(savedPayments.get(0).getAmount()).isEqualByComparingTo("1500.00");
        assertThat(savedPayments.get(0).getImpactAmount()).isEqualByComparingTo("1700.00");
        assertThat(savedPayments.get(0).getAllocationSummary()).contains("valor contado");
        assertThat(savedPayments.get(1).getAmount()).isEqualByComparingTo("500.00");
        assertThat(savedPayments.get(1).getImpactAmount()).isEqualByComparingTo("700.00");
        assertThat(savedPayments.get(1).getAllocationSummary()).contains("valor contado");
    }

    @Test
    void carryForwardPartialCreditUsesRealMoneyInsteadOfProportionalImpact() {
        CreditAccount account = account(12L, PaymentFrequency.WEEKLY, "11100.00");
        CreditInstallment firstInstallment = installment(account, 121L, 1, "5550.00", LocalDate.now());
        CreditInstallment secondInstallment = installment(account, 122L, 2, "5550.00", LocalDate.now().plusWeeks(1));
        List<CreditInstallment> installments = List.of(firstInstallment, secondInstallment);

        mockAccount(account, installments, new BigDecimal("1.26"));

        CreditPayment payment = service.registerPayment(
                12L,
                new BigDecimal("4500.00"),
                null,
                "tester",
                PaymentCollectionMethod.CASH,
                null
        );

        assertThat(firstInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstInstallment.getPaidAmount()).isEqualByComparingTo("5550.00");
        assertThat(secondInstallment.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("50.00");
        assertThat(account.getBalance()).isEqualByComparingTo("5500.00");
        assertThat(payment.getImpactAmount()).isEqualByComparingTo("5600.00");
    }

    @Test
    void paymentAfterDueDateUsesFinancedValue() {
        CreditAccount account = account(2L, PaymentFrequency.WEEKLY, "1200.00");
        CreditInstallment installment = installment(account, 21L, 1, "1200.00", LocalDate.now().minusDays(1));
        List<CreditInstallment> installments = List.of(installment);

        mockAccount(account, installments, new BigDecimal("1.20"));

        CreditPayment payment = service.registerPayment(
                2L,
                new BigDecimal("1000.00"),
                null,
                "tester",
                PaymentCollectionMethod.CASH,
                null
        );

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("1000.00");
        assertThat(account.getBalance()).isEqualByComparingTo("200.00");
        assertThat(payment.getImpactAmount()).isEqualByComparingTo("1000.00");
        assertThat(payment.getAllocationSummary()).contains("valor financiado");
    }

    @Test
    void selectedInstallmentStillCarriesExcessToNextInstallment() {
        CreditAccount account = account(4L, PaymentFrequency.WEEKLY, "1200.00");
        CreditInstallment firstInstallment = installment(account, 41L, 1, "480.00", LocalDate.now());
        CreditInstallment secondInstallment = installment(account, 42L, 2, "480.00", LocalDate.now().plusWeeks(1));
        CreditInstallment thirdInstallment = installment(account, 43L, 3, "240.00", LocalDate.now().plusWeeks(2));
        List<CreditInstallment> installments = List.of(firstInstallment, secondInstallment, thirdInstallment);

        mockAccount(account, installments, new BigDecimal("1.20"));

        service.registerPayment(
                4L,
                new BigDecimal("1000.00"),
                List.of(41L),
                "tester",
                PaymentCollectionMethod.CASH,
                null
        );

        assertThat(firstInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstInstallment.getPaidAmount()).isEqualByComparingTo("480.00");
        assertThat(secondInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("480.00");
        assertThat(thirdInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(thirdInstallment.getPaidAmount()).isEqualByComparingTo("240.00");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void dailyPlanNeverUsesCashValue() {
        CreditAccount account = account(3L, PaymentFrequency.DAILY, "1200.00");
        CreditInstallment installment = installment(account, 31L, 1, "1200.00", LocalDate.now().plusDays(1));
        List<CreditInstallment> installments = List.of(installment);

        mockAccount(account, installments, new BigDecimal("1.20"));

        CreditPayment payment = service.registerPayment(
                3L,
                new BigDecimal("1000.00"),
                null,
                "tester",
                PaymentCollectionMethod.CASH,
                null
        );

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("1000.00");
        assertThat(account.getBalance()).isEqualByComparingTo("200.00");
        assertThat(payment.getImpactAmount()).isEqualByComparingTo("1000.00");
        assertThat(payment.getAllocationSummary()).contains("valor financiado");
    }

    @Test
    void partialPaymentOnSameInstallmentKeepsCashProportionalImpact() {
        CreditAccount account = account(13L, PaymentFrequency.WEEKLY, "1200.00");
        CreditInstallment installment = installment(account, 131L, 1, "1200.00", LocalDate.now());
        List<CreditInstallment> installments = List.of(installment);

        mockAccount(account, installments, new BigDecimal("1.20"));

        CreditPayment payment = service.registerPayment(
                13L,
                new BigDecimal("500.00"),
                null,
                "tester",
                PaymentCollectionMethod.CASH,
                null
        );

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("600.00");
        assertThat(account.getBalance()).isEqualByComparingTo("600.00");
        assertThat(payment.getImpactAmount()).isEqualByComparingTo("600.00");
        assertThat(payment.getAllocationSummary()).contains("valor contado");
    }

    @Test
    void voidingRestoredInstallmentKeepsUsingTheLatestActiveRestoration() {
        CreditAccount account = account(5L, PaymentFrequency.WEEKLY, "200.00");
        CreditInstallment originalInstallment = installment(account, 51L, 1, "100.00", LocalDate.now());
        CreditInstallment secondInstallment = installment(account, 52L, 2, "100.00", LocalDate.now().plusWeeks(1));
        List<CreditInstallment> installments = new ArrayList<>(List.of(originalInstallment, secondInstallment));
        List<CreditPayment> payments = new ArrayList<>();

        mockStatefulAccount(account, installments, payments, new BigDecimal("1.20"));

        service.registerPayment(
                5L,
                new BigDecimal("100.00"),
                List.of(51L),
                "tester",
                PaymentCollectionMethod.BANK,
                null
        );

        service.voidInstallment(5L, 51L, "tester", "primera anulacion");

        CreditInstallment firstRestoredInstallment = installments.stream()
                .filter(installment -> Long.valueOf(51L).equals(installment.getRestoredFromInstallmentId()))
                .filter(installment -> !installment.isVoided())
                .findFirst()
                .orElseThrow();

        service.voidInstallment(5L, firstRestoredInstallment.getId(), "tester", "segunda anulacion");

        CreditInstallment latestRestoredInstallment = installments.stream()
                .filter(installment -> firstRestoredInstallment.getId().equals(installment.getRestoredFromInstallmentId()))
                .filter(installment -> !installment.isVoided())
                .findFirst()
                .orElseThrow();

        assertThat(latestRestoredInstallment.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(latestRestoredInstallment.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(account.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(account.getBalance()).isEqualByComparingTo("200.00");
        assertThat(payments).filteredOn(CreditPayment::isReversal).hasSize(1);
    }

    @Test
    void findBySaleIdRejectsAccountsFromVoidedSales() {
        Sale sale = new Sale();
        sale.setId(88L);
        sale.setStatus(SaleStatus.VOID);

        CreditAccount account = account(6L, PaymentFrequency.WEEKLY, "500.00");
        account.setSale(sale);

        when(creditAccountRepository.findBySale_Id(88L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.findBySaleId(88L))
                .isInstanceOf(InvalidSaleException.class)
                .hasMessageContaining("venta anulada");
    }

    @Test
    void findAllSkipsAccountsFromVoidedSales() {
        CreditAccount activeAccount = account(7L, PaymentFrequency.WEEKLY, "500.00");
        Sale activeSale = new Sale();
        activeSale.setId(91L);
        activeSale.setStatus(SaleStatus.ACTIVE);
        activeAccount.setSale(activeSale);

        CreditAccount hiddenAccount = account(8L, PaymentFrequency.WEEKLY, "500.00");
        Sale voidedSale = new Sale();
        voidedSale.setId(92L);
        voidedSale.setStatus(SaleStatus.VOID);
        hiddenAccount.setSale(voidedSale);

        when(creditAccountRepository.findAll()).thenReturn(List.of(activeAccount, hiddenAccount));

        List<CreditAccount> accounts = service.findAll();

        assertThat(accounts).containsExactly(activeAccount);
    }

    @Test
    void countMoroseClientsIgnoresAccountsFromVoidedSales() {
        CreditAccount visibleAccount = account(9L, PaymentFrequency.WEEKLY, "500.00");
        Sale activeSale = new Sale();
        activeSale.setId(101L);
        activeSale.setStatus(SaleStatus.ACTIVE);
        visibleAccount.setSale(activeSale);
        visibleAccount.setClient(new com.sales.maidav.model.client.Client());
        visibleAccount.getClient().setId(201L);

        CreditAccount hiddenAccount = account(10L, PaymentFrequency.WEEKLY, "500.00");
        Sale voidedSale = new Sale();
        voidedSale.setId(102L);
        voidedSale.setStatus(SaleStatus.VOID);
        hiddenAccount.setSale(voidedSale);
        hiddenAccount.setClient(new com.sales.maidav.model.client.Client());
        hiddenAccount.getClient().setId(202L);

        CreditInstallment visibleInstallment = installment(visibleAccount, 401L, 1, "100.00", LocalDate.now().minusDays(1));
        CreditInstallment hiddenInstallment = installment(hiddenAccount, 402L, 1, "100.00", LocalDate.now().minusDays(1));

        when(creditInstallmentRepository.findByStatusInAndDueDateBefore(
                List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL),
                LocalDate.now()
        )).thenReturn(List.of(visibleInstallment, hiddenInstallment));

        long moroseClients = service.countMoroseClients();

        assertThat(moroseClients).isEqualTo(1);
    }

    private void mockAccount(CreditAccount account, List<CreditInstallment> installments, BigDecimal recargo) {
        CompanySettings settings = new CompanySettings();
        settings.setCalcRecargo(recargo);
        List<CreditPayment> payments = new ArrayList<>();

        when(creditAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId())).thenReturn(installments);
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(creditPaymentRepository.save(any(CreditPayment.class))).thenAnswer(invocation -> {
            CreditPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId((long) payments.size() + 1);
            }
            payments.add(payment);
            return payment;
        });
        when(creditPaymentRepository.findByAccount_IdOrderByPaidAtAscIdAsc(account.getId())).thenAnswer(invocation -> payments.stream()
                .sorted(Comparator.comparing(CreditPayment::getPaidAt)
                        .thenComparing(CreditPayment::getId, Comparator.nullsLast(Long::compareTo)))
                .toList());
    }

    private void mockStatefulAccount(CreditAccount account,
                                     List<CreditInstallment> installments,
                                     List<CreditPayment> payments,
                                     BigDecimal recargo) {
        CompanySettings settings = new CompanySettings();
        settings.setCalcRecargo(recargo);

        AtomicLong nextInstallmentId = new AtomicLong(
                installments.stream()
                        .map(CreditInstallment::getId)
                        .filter(id -> id != null)
                        .max(Long::compareTo)
                        .orElse(0L) + 1
        );
        AtomicLong nextPaymentId = new AtomicLong(1L);

        when(creditAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(creditInstallmentRepository.findById(any(Long.class))).thenAnswer(invocation -> installments.stream()
                .filter(installment -> invocation.getArgument(0).equals(installment.getId()))
                .findFirst());
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId())).thenAnswer(invocation -> installments.stream()
                .sorted(Comparator.comparing(CreditInstallment::getInstallmentNumber)
                        .thenComparing(CreditInstallment::getId, Comparator.nullsLast(Long::compareTo)))
                .toList());
        when(creditInstallmentRepository.save(any(CreditInstallment.class))).thenAnswer(invocation -> {
            CreditInstallment installment = invocation.getArgument(0);
            if (installment.getId() == null) {
                installment.setId(nextInstallmentId.getAndIncrement());
            }
            boolean alreadyPresent = installments.stream()
                    .anyMatch(existing -> installment.getId().equals(existing.getId()));
            if (!alreadyPresent) {
                installments.add(installment);
            }
            return installment;
        });
        when(creditPaymentRepository.findByAccount_IdOrderByPaidAtAscIdAsc(account.getId())).thenAnswer(invocation -> payments.stream()
                .sorted(Comparator.comparing(CreditPayment::getPaidAt)
                        .thenComparing(CreditPayment::getId, Comparator.nullsLast(Long::compareTo)))
                .toList());
        when(creditPaymentRepository.save(any(CreditPayment.class))).thenAnswer(invocation -> {
            CreditPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(nextPaymentId.getAndIncrement());
            }
            boolean alreadyPresent = payments.stream()
                    .anyMatch(existing -> payment.getId().equals(existing.getId()));
            if (!alreadyPresent) {
                payments.add(payment);
            }
            return payment;
        });
        when(companySettingsService.getSettings()).thenReturn(settings);
    }

    private CreditAccount account(Long id, PaymentFrequency paymentFrequency, String totalAmount) {
        CreditAccount account = new CreditAccount();
        account.setId(id);
        account.setPaymentFrequency(paymentFrequency);
        account.setTotalAmount(new BigDecimal(totalAmount));
        account.setBalance(new BigDecimal(totalAmount));
        account.setStatus(AccountStatus.OPEN);
        return account;
    }

    private CreditInstallment installment(CreditAccount account, Long id, int number, String amount, LocalDate dueDate) {
        CreditInstallment installment = new CreditInstallment();
        installment.setId(id);
        installment.setAccount(account);
        installment.setInstallmentNumber(number);
        installment.setDueDate(dueDate);
        installment.setAmount(new BigDecimal(amount));
        installment.setPaidAmount(BigDecimal.ZERO.setScale(2));
        installment.setStatus(InstallmentStatus.PENDING);
        return installment;
    }
}


