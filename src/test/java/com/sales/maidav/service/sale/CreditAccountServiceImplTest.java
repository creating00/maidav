package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.CreditPayment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.PaymentFrequency;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("600.00");
        assertThat(account.getBalance()).isEqualByComparingTo("600.00");
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
        assertThat(savedPayments.get(0).getImpactAmount()).isEqualByComparingTo("1800.00");
        assertThat(savedPayments.get(0).getAllocationSummary()).contains("valor contado");
        assertThat(savedPayments.get(1).getAmount()).isEqualByComparingTo("500.00");
        assertThat(savedPayments.get(1).getImpactAmount()).isEqualByComparingTo("600.00");
        assertThat(savedPayments.get(1).getAllocationSummary()).contains("valor contado");
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

    private void mockAccount(CreditAccount account, List<CreditInstallment> installments, BigDecimal recargo) {
        CompanySettings settings = new CompanySettings();
        settings.setCalcRecargo(recargo);

        when(creditAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId())).thenReturn(installments);
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(creditPaymentRepository.save(any(CreditPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
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


