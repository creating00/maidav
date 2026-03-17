package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.CreditPayment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cashPaymentAfterPartialBankPaymentSpreadsToNextInstallment() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")
        );

        CreditAccount account = new CreditAccount();
        account.setId(1L);
        account.setTotalAmount(new BigDecimal("500.00"));
        account.setBalance(new BigDecimal("500.00"));
        account.setStatus(AccountStatus.OPEN);

        CreditInstallment firstInstallment = installment(account, 11L, 1, "250.00");
        CreditInstallment secondInstallment = installment(account, 12L, 2, "250.00");
        List<CreditInstallment> installments = List.of(firstInstallment, secondInstallment);

        CompanySettings settings = new CompanySettings();
        settings.setCalcRecargo(new BigDecimal("1.26"));

        when(creditAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(1L)).thenReturn(installments);
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(creditPaymentRepository.save(any(CreditPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPayment(1L, new BigDecimal("110.00"), null, "tester", PaymentCollectionMethod.BANK);
        service.registerPayment(1L, new BigDecimal("340.00"), null, "tester", PaymentCollectionMethod.CASH);

        assertThat(firstInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(firstInstallment.getPaidAmount()).isEqualByComparingTo("250.00");
        assertThat(secondInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(secondInstallment.getPaidAmount()).isEqualByComparingTo("250.00");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);

        ArgumentCaptor<CreditPayment> paymentCaptor = ArgumentCaptor.forClass(CreditPayment.class);
        verify(creditPaymentRepository, times(2)).save(paymentCaptor.capture());

        List<CreditPayment> savedPayments = paymentCaptor.getAllValues();
        CreditPayment firstPayment = savedPayments.get(0);
        CreditPayment secondPayment = savedPayments.get(1);

        assertThat(firstPayment.getImpactAmount()).isEqualByComparingTo("110.00");
        assertThat(firstPayment.getAllocationSummary()).contains("Cuota #1 parcial");
        assertThat(secondPayment.getImpactAmount()).isEqualByComparingTo("390.00");
        assertThat(secondPayment.getAllocationSummary()).contains("Cuota #1 total (efectivo sin descuento)");
        assertThat(secondPayment.getAllocationSummary()).contains("Cuota #2 total (efectivo con descuento)");
    }

    private CreditInstallment installment(CreditAccount account, Long id, int number, String amount) {
        CreditInstallment installment = new CreditInstallment();
        installment.setId(id);
        installment.setAccount(account);
        installment.setInstallmentNumber(number);
        installment.setDueDate(LocalDate.now().plusDays(number));
        installment.setAmount(new BigDecimal(amount));
        installment.setPaidAmount(BigDecimal.ZERO.setScale(2));
        installment.setStatus(InstallmentStatus.PENDING);
        return installment;
    }
}
