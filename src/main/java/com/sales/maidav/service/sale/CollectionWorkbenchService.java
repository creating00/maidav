package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.CreditPayment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class CollectionWorkbenchService {

    private final CreditAccountService creditAccountService;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final SaleItemRepository saleItemRepository;
    private final CompanySettingsService companySettingsService;

    public CollectionWorkbenchService(CreditAccountService creditAccountService,
                                      CreditInstallmentRepository creditInstallmentRepository,
                                      SaleItemRepository saleItemRepository,
                                      CompanySettingsService companySettingsService) {
        this.creditAccountService = creditAccountService;
        this.creditInstallmentRepository = creditInstallmentRepository;
        this.saleItemRepository = saleItemRepository;
        this.companySettingsService = companySettingsService;
    }

    public CollectionLookupView lookup(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Ingrese un ID de credito valido");
        }

        CreditAccount account = creditAccountService.findById(accountId);
        List<CreditInstallment> installments = creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(accountId);
        CreditInstallment currentInstallment = installments.stream()
                .filter(installment -> installment.getStatus() != InstallmentStatus.PAID)
                .filter(installment -> installment.getStatus() != InstallmentStatus.VOID)
                .filter(installment -> !installment.isVoided())
                .findFirst()
                .orElse(null);

        BigDecimal financedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal chargeAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseChargeAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal appliedCreditAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal expiredCashAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Integer installmentNumber = null;
        boolean cashPricingAvailable = false;
        String chargeModeLabel = "Valor financiado";
        if (currentInstallment != null) {
            installmentNumber = currentInstallment.getInstallmentNumber();
            BigDecimal remaining = remainingAmount(currentInstallment.getAmount(), currentInstallment.getPaidAmount());
            BigDecimal fullCashAmount = CreditPaymentPricingSupport.resolveInstallmentCashValue(
                    currentInstallment.getAmount(),
                    resolveCashRecargo(),
                    account.getPaymentFrequency()
            );
            financedAmount = normalize(remaining);
            cashPricingAvailable = CreditPaymentPricingSupport.usesCashValue(
                    account.getPaymentFrequency(),
                    currentInstallment.getDueDate(),
                    LocalDate.now()
            );
            cashAmount = cashPricingAvailable
                    ? resolveCashAmount(account, currentInstallment, remaining)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            chargeAmount = CreditPaymentPricingSupport.resolveCollectedAmountDue(
                    remaining,
                    resolveCashRecargo(),
                    account.getPaymentFrequency(),
                    currentInstallment.getDueDate(),
                    LocalDate.now()
            );
            chargeModeLabel = cashPricingAvailable ? "Valor contado" : "Valor financiado";
            appliedCreditAmount = cashPricingAvailable
                    ? normalize(fullCashAmount.subtract(chargeAmount))
                    : normalize(currentInstallment.getAmount().subtract(financedAmount));
            if (appliedCreditAmount.compareTo(BigDecimal.ZERO) < 0) {
                appliedCreditAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            baseChargeAmount = normalize(chargeAmount.add(appliedCreditAmount));
            if (!cashPricingAvailable && fullCashAmount.compareTo(BigDecimal.ZERO) > 0) {
                expiredCashAmount = fullCashAmount;
            }
        }

        boolean overdue = installments.stream()
                .anyMatch(installment -> installment.getStatus() != InstallmentStatus.PAID
                        && installment.getStatus() != InstallmentStatus.VOID
                        && !installment.isVoided()
                        && installment.getDueDate() != null
                        && installment.getDueDate().isBefore(LocalDate.now()));
        // MOSTRAR MONTO ATRASO
        BigDecimal overdueAmount = installments.stream()
                .filter(installment -> installment.getStatus() != InstallmentStatus.PAID)
                .filter(installment -> installment.getStatus() != InstallmentStatus.VOID)
                .filter(installment -> !installment.isVoided())
                .filter(installment -> installment.getDueDate() != null && installment.getDueDate().isBefore(LocalDate.now()))
                .map(installment -> remainingAmount(installment.getAmount(), installment.getPaidAmount()))
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        return new CollectionLookupView(
                account.getId(),
                account.getAccountNumber(),
                buildClientName(account),
                buildProductSummary(account),
                installmentNumber,
                cashAmount,
                financedAmount,
                chargeAmount,
                baseChargeAmount,
                appliedCreditAmount,
                expiredCashAmount,
                normalize(account.getBalance()),
                normalize(overdueAmount),
                overdue ? "Atrasado" : "Al dia",
                overdue ? "estadoA" : "estadoD",
                cashPricingAvailable,
                chargeModeLabel,
                "/accounts/" + account.getId() + "#payments-section"
        );
    }

    public PaymentSubmitResult registerPayment(Long accountId,
                                               BigDecimal amount,
                                               PaymentCollectionMethod paymentMethod,
                                               String registeredBy,
                                               String operationToken) {
        CreditPayment payment = creditAccountService.registerPayment(
                accountId,
                amount,
                null,
                registeredBy,
                paymentMethod,
                operationToken
        );
        return new PaymentSubmitResult(
                normalize(payment.getAmount()),
                payment.getPaymentMethod() == PaymentCollectionMethod.CASH ? "Efectivo" : "Transferencia / Debito",
                "/accounts/" + accountId + "#payments-section"
        );
    }

    private String buildClientName(CreditAccount account) {
        if (account == null || account.getClient() == null) {
            return "-";
        }
        String firstName = account.getClient().getFirstName() == null ? "" : account.getClient().getFirstName().trim();
        String lastName = account.getClient().getLastName() == null ? "" : account.getClient().getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "-" : fullName;
    }

    private String buildProductSummary(CreditAccount account) {
        if (account == null || account.getSale() == null || account.getSale().getId() == null) {
            return "-";
        }
        List<SaleItem> saleItems = saleItemRepository.findBySale_IdOrderByIdAsc(account.getSale().getId());
        if (saleItems.isEmpty()) {
            return "-";
        }
        if (saleItems.size() == 1) {
            return productLabel(saleItems.get(0));
        }
        String first = productLabel(saleItems.get(0));
        String second = productLabel(saleItems.get(1));
        int remaining = saleItems.size() - 2;
        if (remaining > 0) {
            return first + ", " + second + " +" + remaining + " mas";
        }
        return first + ", " + second;
    }

    private String productLabel(SaleItem saleItem) {
        if (saleItem == null || saleItem.getProduct() == null) {
            return "Producto";
        }
        String description = saleItem.getProduct().getDescription();
        return description == null || description.isBlank() ? "Producto" : description.trim();
    }

    private BigDecimal resolveCashAmount(CreditAccount account,
                                         CreditInstallment installment,
                                         BigDecimal financedAmount) {
        return CreditPaymentPricingSupport.resolveCollectedAmountDue(
                financedAmount,
                resolveCashRecargo(),
                account.getPaymentFrequency(),
                installment.getDueDate(),
                LocalDate.now()
        );
    }

    private BigDecimal resolveCashRecargo() {
        CompanySettings settings = companySettingsService.getSettings();
        BigDecimal recargo = settings.getCalcRecargo();
        if (recargo == null || recargo.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1.26");
        }
        return recargo;
    }

    private BigDecimal remainingAmount(BigDecimal amount, BigDecimal paidAmount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal remaining = amount.subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return remaining.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record CollectionLookupView(Long accountId,
                                       String accountNumber,
                                       String clientName,
                                       String productName,
                                       Integer installmentNumber,
                                       BigDecimal cashAmount,
                                       BigDecimal financedAmount,
                                       BigDecimal chargeAmount,
                                       BigDecimal baseChargeAmount,
                                       BigDecimal appliedCreditAmount,
                                       BigDecimal expiredCashAmount,
                                       BigDecimal balance,
                                       BigDecimal overdueAmount,
                                       String statusLabel,
                                       String statusCssClass,
                                       boolean cashPricingAvailable,
                                       String chargeModeLabel,
                                       String detailUrl) {
    }

    public record PaymentSubmitResult(BigDecimal amount, String paymentMethodLabel, String detailUrl) {
    }
}

