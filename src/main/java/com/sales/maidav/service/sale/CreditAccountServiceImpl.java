package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.*;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditAccountServiceImpl implements CreditAccountService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final CompanySettingsService companySettingsService;
    private final UserRepository userRepository;

    @Override
    public List<CreditAccount> findAll() {
        if (isCurrentUserAdmin()) {
            return creditAccountRepository.findAll();
        }
        Long sellerId = currentUserId();
        return sellerId == null ? List.of() : creditAccountRepository.findBySale_Seller_Id(sellerId);
    }

    @Override
    public CreditAccount findById(Long id) {
        if (isCurrentUserAdmin()) {
            return creditAccountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        }
        Long sellerId = currentUserId();
        return creditAccountRepository.findByIdAndSale_Seller_Id(id, sellerId == null ? -1L : sellerId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
    }

    @Override
    public CreditAccount findBySaleId(Long saleId) {
        if (isCurrentUserAdmin()) {
            return creditAccountRepository.findBySale_Id(saleId)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        }
        Long sellerId = currentUserId();
        return creditAccountRepository.findBySale_IdAndSale_Seller_Id(saleId, sellerId == null ? -1L : sellerId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount) {
        registerPayment(accountId, amount, null, null, PaymentCollectionMethod.BANK);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds) {
        registerPayment(accountId, amount, installmentIds, null, PaymentCollectionMethod.BANK);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds, String registeredBy,
                                PaymentCollectionMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago debe ser mayor a cero");
        }
        CreditAccount account = findById(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidSaleException("La cuenta ya esta saldada");
        }

        CreditPayment payment = new CreditPayment();
        payment.setAccount(account);
        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(LocalDate.now());
        payment.setRegisteredBy(trimToNull(registeredBy));
        PaymentCollectionMethod resolvedMethod = paymentMethod == null ? PaymentCollectionMethod.BANK : paymentMethod;
        payment.setPaymentMethod(resolvedMethod);

        List<CreditInstallment> installments = resolveInstallments(accountId, installmentIds);
        BigDecimal maxPayable = calculateMaxPayable(installments, resolvedMethod);
        if (amount.compareTo(maxPayable) > 0) {
            throw new InvalidSaleException("El pago supera el saldo de las cuotas seleccionadas");
        }
        Map<Integer, String> paymentReferences = new LinkedHashMap<>();
        BigDecimal impactAmount = applyPaymentToInstallments(
                installments,
                amount.setScale(2, RoundingMode.HALF_UP),
                resolvedMethod,
                LocalDate.now(),
                paymentReferences
        );

        if (impactAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago no pudo aplicarse a cuotas pendientes");
        }
        BigDecimal newBalance = account.getBalance().subtract(impactAmount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El pago supera el saldo");
        }

        payment.setImpactAmount(impactAmount.setScale(2, RoundingMode.HALF_UP));
        payment.setAllocationSummary(buildAllocationSummary(paymentReferences));
        creditPaymentRepository.save(payment);
        account.setBalance(newBalance.setScale(2, RoundingMode.HALF_UP));
        if (account.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            account.setStatus(AccountStatus.CLOSED);
        }
    }

    @Override
    public void updatePayment(Long accountId, Long paymentId, BigDecimal amount, LocalDate paidAt,
                              PaymentCollectionMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago debe ser mayor a cero");
        }
        if (paidAt == null) {
            throw new InvalidSaleException("La fecha de pago es obligatoria");
        }

        CreditAccount account = findById(accountId);
        CreditPayment payment = creditPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new InvalidSaleException("Pago no encontrado"));
        if (!payment.getAccount().getId().equals(accountId)) {
            throw new InvalidSaleException("El pago no pertenece a la cuenta");
        }

        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(paidAt);
        payment.setPaymentMethod(paymentMethod == null ? PaymentCollectionMethod.BANK : paymentMethod);

        recalculateAccountState(account);
    }

    @Override
    public void updateInstallmentDueDate(Long accountId, Long installmentId, LocalDate dueDate) {
        if (dueDate == null) {
            throw new InvalidSaleException("La fecha de vencimiento es obligatoria");
        }
        CreditInstallment installment = creditInstallmentRepository.findById(installmentId)
                .orElseThrow(() -> new InvalidSaleException("Cuota no encontrada"));
        if (!installment.getAccount().getId().equals(accountId)) {
            throw new InvalidSaleException("La cuota no pertenece a la cuenta");
        }
        installment.setDueDate(dueDate);
    }

    private List<CreditInstallment> resolveInstallments(Long accountId, List<Long> installmentIds) {
        if (installmentIds == null || installmentIds.isEmpty()) {
            return creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(accountId);
        }
        return creditInstallmentRepository.findByAccount_IdAndIdInOrderByInstallmentNumber(accountId, installmentIds);
    }

    private BigDecimal applyPaymentToInstallments(List<CreditInstallment> installments,
                                                  BigDecimal inputAmount,
                                                  PaymentCollectionMethod paymentMethod,
                                                  LocalDate paidAt,
                                                  Map<Integer, String> paymentReferences) {
        BigDecimal remainingInput = inputAmount == null ? BigDecimal.ZERO : inputAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal impactAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashRecargo = resolveCashRecargo();

        for (int index = 0; index < installments.size(); index++) {
            CreditInstallment installment = installments.get(index);
            if (remainingInput.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (installment.getStatus() == InstallmentStatus.PAID) {
                continue;
            }

            BigDecimal paidAmount = installment.getPaidAmount() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : installment.getPaidAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal financedRemaining = installment.getAmount().subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
            if (financedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                installment.setStatus(InstallmentStatus.PAID);
                continue;
            }

            if (paymentMethod == PaymentCollectionMethod.CASH) {
                BigDecimal cashRemaining = calculateCashInstallmentAmount(financedRemaining);
                boolean discountEligible = paidAmount.compareTo(BigDecimal.ZERO) == 0;
                boolean closesWithCashDiscount = discountEligible
                        && remainingInput.compareTo(cashRemaining) >= 0
                        && (remainingInput.compareTo(cashRemaining) == 0
                        || hasPendingInstallmentAfter(installments, index));
                if (closesWithCashDiscount) {
                    installment.setPaidAmount(installment.getAmount().setScale(2, RoundingMode.HALF_UP));
                    installment.setStatus(InstallmentStatus.PAID);
                    installment.setPaidAt(paidAt);
                    remainingInput = remainingInput.subtract(cashRemaining).setScale(2, RoundingMode.HALF_UP);
                    impactAmount = impactAmount.add(financedRemaining).setScale(2, RoundingMode.HALF_UP);
                    paymentReferences.put(
                            installment.getInstallmentNumber(),
                            "Cuota #" + installment.getInstallmentNumber() + " total (efectivo con descuento)"
                    );
                } else {
                    BigDecimal partialImpact = remainingInput.min(financedRemaining).setScale(2, RoundingMode.HALF_UP);
                    installment.setPaidAmount(paidAmount.add(partialImpact).setScale(2, RoundingMode.HALF_UP));
                    installment.setStatus(
                            installment.getPaidAmount().compareTo(installment.getAmount().setScale(2, RoundingMode.HALF_UP)) >= 0
                                    ? InstallmentStatus.PAID
                                    : InstallmentStatus.PARTIAL
                    );
                    installment.setPaidAt(paidAt);
                    impactAmount = impactAmount.add(partialImpact).setScale(2, RoundingMode.HALF_UP);
                    paymentReferences.put(
                            installment.getInstallmentNumber(),
                            installment.getStatus() == InstallmentStatus.PAID
                                    ? "Cuota #" + installment.getInstallmentNumber() + " total (efectivo sin descuento)"
                                    : "Cuota #" + installment.getInstallmentNumber() + " parcial (efectivo sin descuento)"
                    );
                    remainingInput = remainingInput.subtract(partialImpact).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                if (remainingInput.compareTo(financedRemaining) >= 0) {
                    installment.setPaidAmount(installment.getAmount().setScale(2, RoundingMode.HALF_UP));
                    installment.setStatus(InstallmentStatus.PAID);
                    installment.setPaidAt(paidAt);
                    remainingInput = remainingInput.subtract(financedRemaining).setScale(2, RoundingMode.HALF_UP);
                    impactAmount = impactAmount.add(financedRemaining).setScale(2, RoundingMode.HALF_UP);
                    paymentReferences.put(
                            installment.getInstallmentNumber(),
                            "Cuota #" + installment.getInstallmentNumber() + " total"
                    );
                } else {
                    BigDecimal partialImpact = remainingInput.min(financedRemaining).setScale(2, RoundingMode.HALF_UP);
                    installment.setPaidAmount(paidAmount.add(partialImpact).setScale(2, RoundingMode.HALF_UP));
                    installment.setStatus(InstallmentStatus.PARTIAL);
                    installment.setPaidAt(paidAt);
                    impactAmount = impactAmount.add(partialImpact).setScale(2, RoundingMode.HALF_UP);
                    paymentReferences.put(
                            installment.getInstallmentNumber(),
                            "Cuota #" + installment.getInstallmentNumber() + " parcial"
                    );
                    remainingInput = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        return impactAmount;
    }

    private BigDecimal calculateMaxPayable(List<CreditInstallment> installments, PaymentCollectionMethod paymentMethod) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CreditInstallment installment : installments) {
            if (installment.getStatus() == InstallmentStatus.PAID) {
                continue;
            }
            BigDecimal paidAmount = installment.getPaidAmount() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : installment.getPaidAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal financedRemaining = installment.getAmount().subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
            if (financedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(financedRemaining).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private boolean hasPendingInstallmentAfter(List<CreditInstallment> installments, int currentIndex) {
        for (int i = currentIndex + 1; i < installments.size(); i++) {
            if (installments.get(i).getStatus() != InstallmentStatus.PAID) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal resolveCashRecargo() {
        BigDecimal recargo = companySettingsService.getSettings().getCalcRecargo();
        if (recargo == null || recargo.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1.26");
        }
        return recargo;
    }

    private BigDecimal calculateCashInstallmentAmount(BigDecimal financedAmount) {
        if (financedAmount == null || financedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal recargo = resolveCashRecargo();
        BigDecimal rawCashAmount = financedAmount.divide(recargo, 2, RoundingMode.HALF_UP);
        return roundUpToFifty(rawCashAmount);
    }

    private BigDecimal roundUpToFifty(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = new BigDecimal("50");
        return amount
                .divide(factor, 0, RoundingMode.CEILING)
                .multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void recalculateAccountState(CreditAccount account) {
        List<CreditInstallment> installments = creditInstallmentRepository
                .findByAccount_IdOrderByInstallmentNumber(account.getId());
        List<CreditPayment> payments = creditPaymentRepository
                .findByAccount_IdOrderByPaidAtAscIdAsc(account.getId());

        for (CreditInstallment installment : installments) {
            installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            installment.setStatus(InstallmentStatus.PENDING);
            installment.setPaidAt(null);
        }

        for (CreditPayment payment : payments) {
            Map<Integer, String> paymentReferences = new LinkedHashMap<>();
            BigDecimal impactAmount = applyPaymentToInstallments(
                    installments,
                    payment.getAmount(),
                    payment.getPaymentMethod() == null ? PaymentCollectionMethod.BANK : payment.getPaymentMethod(),
                    payment.getPaidAt(),
                    paymentReferences
            );
            payment.setImpactAmount(impactAmount.setScale(2, RoundingMode.HALF_UP));
            payment.setAllocationSummary(buildAllocationSummary(paymentReferences));
        }

        BigDecimal totalPaid = payments.stream()
                .map(CreditPayment::getImpactAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = account.getTotalAmount().subtract(totalPaid).setScale(2, RoundingMode.HALF_UP);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("La suma de pagos no puede superar el total de la cuenta");
        }
        account.setBalance(newBalance);
        account.setStatus(newBalance.compareTo(BigDecimal.ZERO) == 0 ? AccountStatus.CLOSED : AccountStatus.OPEN);
    }

    @Override
    public long countMoroseClients() {
        return creditInstallmentRepository
                .findByStatusInAndDueDateBefore(
                        List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL),
                        LocalDate.now()
                )
                .stream()
                .map(inst -> inst.getAccount().getClient().getId())
                .distinct()
                .count();
    }

    @Override
    public List<MorositySummary> getMorosity(MorosityLevel levelFilter) {
        LocalDate today = LocalDate.now();
        List<CreditAccount> accounts = creditAccountRepository.findAll();

        Map<Long, ClientAggregate> aggregates = new HashMap<>();
        for (CreditAccount account : accounts) {
            Long clientId = account.getClient().getId();
            aggregates.putIfAbsent(clientId, new ClientAggregate(account.getClient()));
        }

        creditInstallmentRepository
                .findByStatusInAndDueDateBefore(
                        List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL),
                        today
                )
                .forEach(installment -> {
                    Long clientId = installment.getAccount().getClient().getId();
                    ClientAggregate agg = aggregates.computeIfAbsent(
                            clientId, id -> new ClientAggregate(installment.getAccount().getClient())
                    );
                    long days = ChronoUnit.DAYS.between(installment.getDueDate(), today);
                    if (days > agg.maxDaysOverdue) {
                        agg.maxDaysOverdue = days;
                    }
                    BigDecimal paidAmount =
                            installment.getPaidAmount() == null ? BigDecimal.ZERO : installment.getPaidAmount();
                    BigDecimal remaining = installment.getAmount().subtract(paidAmount);
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        agg.amountDue = agg.amountDue.add(remaining);
                    }
                });

        List<MorositySummary> result = new ArrayList<>();
        for (ClientAggregate agg : aggregates.values()) {
            MorosityLevel level = resolveLevel(agg.maxDaysOverdue);
            if (levelFilter != null && level != levelFilter) {
                continue;
            }
            result.add(new MorositySummary(
                    agg.client,
                    agg.maxDaysOverdue,
                    agg.amountDue.setScale(2, RoundingMode.HALF_UP),
                    level
            ));
        }

        result.sort(Comparator.comparing(MorositySummary::getDaysOverdue).reversed());
        return result;
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private MorosityLevel resolveLevel(long daysOverdue) {
        if (daysOverdue <= 0) {
            return MorosityLevel.BUENO;
        }
        if (daysOverdue <= 7) {
            return MorosityLevel.MEDIO;
        }
        return MorosityLevel.ALTO;
    }

    private static class ClientAggregate {
        private final com.sales.maidav.model.client.Client client;
        private long maxDaysOverdue = 0;
        private BigDecimal amountDue = BigDecimal.ZERO;

        private ClientAggregate(com.sales.maidav.model.client.Client client) {
            this.client = client;
        }
    }

    private String buildAllocationSummary(Map<Integer, String> paymentReferences) {
        if (paymentReferences == null || paymentReferences.isEmpty()) {
            return null;
        }
        return String.join(" | ", paymentReferences.values());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
