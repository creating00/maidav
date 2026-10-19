package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.settings.CompanySettingsService;
import com.sales.maidav.service.user.UserService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditAccountControllerTest {
    @Mock private CreditAccountService creditAccountService;
    @Mock private CreditInstallmentRepository creditInstallmentRepository;
    @Mock private CreditPaymentRepository creditPaymentRepository;
    @Mock private CompanySettingsService companySettingsService;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private UserService userService;
    @InjectMocks private CreditAccountController controller;

    @ParameterizedTest
    @CsvSource({
            "DAILY, -1, false, 1500.00, 0.00",
            "DAILY, 0, false, 1500.00, 0.00",
            "DAILY, 1, false, 1500.00, 0.00",
            "WEEKLY, -1, false, 1500.00, 1200.00",
            "WEEKLY, 0, true, 1200.00, 0.00",
            "MONTHLY, 1, true, 1200.00, 0.00"
    })
    void detailShowsCashBenefitOnlyForEligiblePlansIncludingLegacyAmounts(
            PaymentFrequency frequency, int dueOffset, boolean cashAvailable,
            BigDecimal charge, BigDecimal expiredCash) {
        CreditAccount account = new CreditAccount();
        account.setId(1L);
        account.setPaymentFrequency(frequency);
        CreditInstallment installment = new CreditInstallment();
        installment.setId(2L);
        installment.setAccount(account);
        installment.setInstallmentNumber(1);
        installment.setDueDate(LocalDate.now().plusDays(dueOffset));
        installment.setAmount(new BigDecimal("1500.00"));
        installment.setCashAmount(new BigDecimal("1200.00"));
        installment.setPaidAmount(BigDecimal.ZERO);
        installment.setStatus(InstallmentStatus.PENDING);
        when(creditAccountService.findById(1L)).thenReturn(account);
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(1L)).thenReturn(List.of(installment));
        when(companySettingsService.getSettings()).thenReturn(new CompanySettings());
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.detail(1L, model, new RedirectAttributesModelMap())).isEqualTo("pages/accounts/detail");

        assertThat((BigDecimal) model.get("currentInstallmentCashAmount")).isEqualByComparingTo(charge);
        assertThat((BigDecimal) model.get("currentInstallmentExpiredCash")).isEqualByComparingTo(expiredCash);
        assertThat(model.get("currentInstallmentCashPricing")).isEqualTo(cashAvailable);
        assertThat(((Map<?, ?>) model.get("expiredCashAmounts")).get(2L)).isEqualTo(expiredCash);
    }
}
