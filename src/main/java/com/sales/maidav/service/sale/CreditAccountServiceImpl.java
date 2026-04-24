package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.*;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditAccountServiceImpl implements CreditAccountService {

    private static final int ALLOCATION_SUMMARY_MAX_LENGTH = 255;

    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final CompanySettingsService companySettingsService;
    private final UserRepository userRepository;

    @Override
    public List<CreditAccount> findAll() {
        if (isCurrentUserAdmin()) {
            return creditAccountRepository.findAll().stream()
                    .filter(this::isVisibleAccount)
                    .toList();
        }
        Long sellerId = currentUserId();
        return sellerId == null ? List.of() : creditAccountRepository.findBySale_Seller_Id(sellerId).stream()
                .filter(this::isVisibleAccount)
                .toList();
    }

    @Override
    public CreditAccount findById(Long id) {
        if (isCurrentUserAdmin()) {
            CreditAccount account = creditAccountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
            return requireVisibleAccount(account);
        }
        Long sellerId = currentUserId();
        CreditAccount account = creditAccountRepository.findByIdAndSale_Seller_Id(id, sellerId == null ? -1L : sellerId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        return requireVisibleAccount(account);
    }

    @Override
    public CreditAccount findBySaleId(Long saleId) {
        if (isCurrentUserAdmin()) {
            CreditAccount account = creditAccountRepository.findBySale_Id(saleId)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
            return requireVisibleAccount(account);
        }
        Long sellerId = currentUserId();
        CreditAccount account = creditAccountRepository.findBySale_IdAndSale_Seller_Id(saleId, sellerId == null ? -1L : sellerId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        return requireVisibleAccount(account);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount) {
        registerPayment(accountId, amount, null, null, PaymentCollectionMethod.BANK, null);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds) {
        registerPayment(accountId, amount, installmentIds, null, PaymentCollectionMethod.BANK, null);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds, String registeredBy,
                                PaymentCollectionMethod paymentMethod) {
        registerPayment(accountId, amount, installmentIds, registeredBy, paymentMethod, null);
    }

    @Override
    public CreditPayment registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds, String registeredBy,
                                         PaymentCollectionMethod paymentMethod, String operationToken) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago debe ser mayor a cero");
        }
        CreditAccount account = findById(accountId);
        if (account.getStatus() == AccountStatus.VOID) {
            throw new InvalidSaleException("La cuenta esta anulada");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidSaleException("La cuenta ya esta saldada");
        }
        String normalizedOperationToken = trimToNull(operationToken);
        if (normalizedOperationToken != null) {
            CreditPayment existingPayment = creditPaymentRepository
                    .findByAccount_IdAndOperationToken(accountId, normalizedOperationToken)
                    .orElse(null);
            if (existingPayment != null) {
                return existingPayment;
            }
        }

        CreditPayment payment = new CreditPayment();
        payment.setAccount(account);
        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(LocalDate.now());
        payment.setRegisteredBy(trimToNull(registeredBy));
        payment.setOperationToken(normalizedOperationToken);
        PaymentCollectionMethod resolvedMethod = paymentMethod == null ? PaymentCollectionMethod.BANK : paymentMethod;
        payment.setPaymentMethod(resolvedMethod);

        List<CreditInstallment> installments = resolveInstallments(accountId, installmentIds);
        PaymentApplicationResult applicationResult = applyPaymentToInstallments(
                account,
                installments,
                amount.setScale(2, RoundingMode.HALF_UP),
                resolvedMethod,
                LocalDate.now(),
                payment.getId(),
                new ArrayList<>()
        );
        BigDecimal impactAmount = applicationResult.impactAmount();

        if (impactAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago no pudo aplicarse a cuotas pendientes");
        }
        BigDecimal newBalance = account.getBalance().subtract(impactAmount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El pago supera el saldo");
        }

        payment.setImpactAmount(impactAmount.setScale(2, RoundingMode.HALF_UP));
        payment.setAllocationSummary(buildAllocationSummary(applicationResult.paymentReferences()));
        try {
            creditPaymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedOperationToken != null) {
                return creditPaymentRepository.findByAccount_IdAndOperationToken(accountId, normalizedOperationToken)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
        account.setBalance(newBalance.setScale(2, RoundingMode.HALF_UP));
        if (account.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            account.setStatus(AccountStatus.CLOSED);
        } else {
            account.setStatus(AccountStatus.OPEN);
        }
        return payment;
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
        if (payment.isReversal()) {
            throw new InvalidSaleException("Las reversiones de anulacion no pueden editarse manualmente");
        }

        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(paidAt);
        payment.setPaymentMethod(paymentMethod == null ? PaymentCollectionMethod.BANK : paymentMethod);

        recalculateAccountState(account);
    }

    @Override
    public void voidInstallment(Long accountId, Long installmentId, String voidedBy, String reason) {
        CreditInstallment installment = creditInstallmentRepository.findById(installmentId)
                .orElseThrow(() -> new InvalidSaleException("Cuota no encontrada"));
        if (!installment.getAccount().getId().equals(accountId)) {
            throw new InvalidSaleException("La cuota no pertenece a la cuenta");
        }
        if (installment.isVoided() || installment.getStatus() == InstallmentStatus.VOID) {
            throw new InvalidSaleException("La cuota ya fue anulada");
        }

        List<PaymentAllocation> currentAllocations = collectCurrentAllocations(installment.getAccount());
        List<PaymentAllocation> installmentAllocations = currentAllocations.stream()
                .filter(allocation -> installmentId.equals(allocation.installmentId()))
                .toList();
        if (!hasCurrentPaymentImpact(installmentAllocations)) {
            throw new InvalidSaleException("Solo se pueden anular cuotas con pagos aplicados");
        }
        List<PaymentAllocation> impactedAllocations = resolveImpactedAllocationsForVoid(
                installment,
                currentAllocations,
                installmentAllocations
        );

        // FIX ANULACION CUOTA
        // AUDITORIA ANULACION
        installment.setVoided(true);
        installment.setStatus(InstallmentStatus.VOID);
        installment.setVoidedAt(LocalDateTime.now());
        installment.setVoidedBy(trimToNull(voidedBy));
        installment.setVoidReason(trimToNull(reason));
        creditInstallmentRepository.save(installment);

        // RESTAURAR CUOTA REIMPACTABLE
        CreditInstallment restoredInstallment = restoreInstallmentForReimpact(installment);
        creditInstallmentRepository.save(restoredInstallment);

        // REVERSION IMPACTO ECONOMICO
        buildReversalPayments(installment, restoredInstallment, impactedAllocations, voidedBy, reason)
                .forEach(creditPaymentRepository::save);

        // RECALCULO DE TOTALES
        recalculateAccountState(installment.getAccount());
    }

    @Override
    public Map<Long, BigDecimal> getAppliedCarryForwardAmounts(Long accountId) {
        CreditAccount account = findById(accountId);
        Map<Long, BigDecimal> carryForwardAmounts = new HashMap<>();
        for (PaymentAllocation allocation : collectCurrentAllocations(account)) {
            if (!allocation.carriedForward()
                    || allocation.installmentId() == null
                    || allocation.collectedAmount() == null
                    || allocation.collectedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            carryForwardAmounts.merge(
                    allocation.installmentId(),
                    allocation.collectedAmount().setScale(2, RoundingMode.HALF_UP),
                    BigDecimal::add
            );
        }
        carryForwardAmounts.replaceAll((installmentId, amount) -> amount.setScale(2, RoundingMode.HALF_UP));
        return carryForwardAmounts;
    }

    private List<CreditInstallment> resolveInstallments(Long accountId, List<Long> installmentIds) {
        List<CreditInstallment> activeInstallments = creditInstallmentRepository
                .findByAccount_IdOrderByInstallmentNumber(accountId)
                .stream()
                .filter(installment -> !installment.isVoided() && installment.getStatus() != InstallmentStatus.VOID)
                .toList();
        if (installmentIds == null || installmentIds.isEmpty()) {
            return activeInstallments;
        }
        int firstSelectedIndex = -1;
        for (int index = 0; index < activeInstallments.size(); index++) {
            if (installmentIds.contains(activeInstallments.get(index).getId())) {
                firstSelectedIndex = index;
                break;
            }
        }
        if (firstSelectedIndex < 0) {
            return List.of();
        }
        return activeInstallments.subList(firstSelectedIndex, activeInstallments.size());
    }

    private PaymentApplicationResult applyPaymentToInstallments(CreditAccount account,
                                                                List<CreditInstallment> installments,
                                                                BigDecimal inputAmount,
                                                                PaymentCollectionMethod paymentMethod,
                                                                LocalDate paidAt,
                                                                Long paymentId,
                                                                List<PaymentAllocation> allocations) {
        BigDecimal remainingInput = inputAmount == null ? BigDecimal.ZERO : inputAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal impactAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashRecargo = resolveCashRecargo();
        Map<Integer, String> paymentReferences = new LinkedHashMap<>();

        for (int index = 0; index < installments.size(); index++) {
            CreditInstallment installment = installments.get(index);
            if (remainingInput.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (installment.getStatus() == InstallmentStatus.PAID || installment.getStatus() == InstallmentStatus.VOID || installment.isVoided()) {
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
            // SALDO A FAVOR
            // APLICAR SALDO A FAVOR A PROXIMA CUOTA
            BigDecimal collectedNeeded = CreditPaymentPricingSupport.resolveCollectedAmountDue(
                    financedRemaining,
                    cashRecargo,
                    paymentMethod,
                    account.getPaymentFrequency(),
                    installment.getDueDate(),
                    paidAt
            );
            BigDecimal collectedApplied = remainingInput.min(collectedNeeded).setScale(2, RoundingMode.HALF_UP);
            BigDecimal allocationImpact = CreditPaymentPricingSupport.resolveImpactAmount(
                    financedRemaining,
                    collectedApplied,
                    cashRecargo,
                    paymentMethod,
                    account.getPaymentFrequency(),
                    installment.getDueDate(),
                    paidAt
            );
            if (allocationImpact.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal newPaidAmount = paidAmount.add(allocationImpact).setScale(2, RoundingMode.HALF_UP);
            installment.setPaidAmount(newPaidAmount);
            installment.setStatus(
                    newPaidAmount.compareTo(installment.getAmount().setScale(2, RoundingMode.HALF_UP)) >= 0
                            ? InstallmentStatus.PAID
                            : InstallmentStatus.PARTIAL
            );
            installment.setPaidAt(paidAt);
            BigDecimal remainingAfterAllocation = installment.getAmount()
                    .subtract(newPaidAmount)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            remainingInput = remainingInput.subtract(collectedApplied).setScale(2, RoundingMode.HALF_UP);
            impactAmount = impactAmount.add(allocationImpact).setScale(2, RoundingMode.HALF_UP);
            paymentReferences.put(
                    installment.getInstallmentNumber(),
                    buildPaymentReference(
                            account,
                            installment,
                            paymentMethod,
                            paidAt,
                            collectedApplied,
                            remainingAfterAllocation,
                            index > 0,
                            installment.getStatus() == InstallmentStatus.PAID
                    )
            );
            allocations.add(new PaymentAllocation(
                    paymentId,
                    installment.getId(),
                    installment.getInstallmentNumber(),
                    paymentMethod,
                    index > 0,
                    collectedApplied,
                    allocationImpact
            ));
        }

        return new PaymentApplicationResult(
                impactAmount.setScale(2, RoundingMode.HALF_UP),
                paymentReferences,
                allocations
        );
    }

    private BigDecimal resolveCashRecargo() {
        BigDecimal recargo = companySettingsService.getSettings().getCalcRecargo();
        if (recargo == null || recargo.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1.26");
        }
        return recargo;
    }

    private void recalculateAccountState(CreditAccount account) {
        List<CreditInstallment> installments = creditInstallmentRepository
                .findByAccount_IdOrderByInstallmentNumber(account.getId());
        List<CreditPayment> payments = creditPaymentRepository
                .findByAccount_IdOrderByPaidAtAscIdAsc(account.getId());
        ReplayResult replayResult = replayAccountState(account, installments, payments, true);
        Map<Long, CreditInstallment> replayInstallmentsById = replayResult.installmentsById();

        for (CreditInstallment installment : installments) {
            if (installment.isVoided() || installment.getStatus() == InstallmentStatus.VOID) {
                installment.setVoided(true);
                installment.setStatus(InstallmentStatus.VOID);
                continue;
            }
            CreditInstallment replayInstallment = replayInstallmentsById.get(installment.getId());
            if (replayInstallment == null) {
                installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                installment.setStatus(InstallmentStatus.PENDING);
                installment.setPaidAt(null);
                continue;
            }
            installment.setPaidAmount(replayInstallment.getPaidAmount());
            installment.setStatus(replayInstallment.getStatus());
            installment.setPaidAt(replayInstallment.getPaidAt());
        }

        List<CreditInstallment> activeInstallments = installments.stream()
                .filter(this::isActiveInstallment)
                .toList();

        BigDecimal recalculatedTotal = activeInstallments.stream()
                .map(CreditInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPaid = payments.stream()
                .map(CreditPayment::getImpactAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = recalculatedTotal.subtract(totalPaid).setScale(2, RoundingMode.HALF_UP);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("No se puede anular la cuota porque los pagos ya superan el saldo recalculado");
        }
        account.setTotalAmount(recalculatedTotal);
        account.setBalance(newBalance);
        account.setStatus(newBalance.compareTo(BigDecimal.ZERO) == 0 ? AccountStatus.CLOSED : AccountStatus.OPEN);
    }

    private CreditInstallment restoreInstallmentForReimpact(CreditInstallment originalInstallment) {
        CreditInstallment restoredInstallment = new CreditInstallment();
        restoredInstallment.setAccount(originalInstallment.getAccount());
        restoredInstallment.setInstallmentNumber(originalInstallment.getInstallmentNumber());
        restoredInstallment.setDueDate(originalInstallment.getDueDate());
        restoredInstallment.setAmount(originalInstallment.getAmount().setScale(2, RoundingMode.HALF_UP));
        restoredInstallment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        restoredInstallment.setStatus(InstallmentStatus.PENDING);
        restoredInstallment.setPaidAt(null);
        restoredInstallment.setRestoredFromInstallmentId(originalInstallment.getId());
        return restoredInstallment;
    }

    private List<PaymentAllocation> collectCurrentAllocations(CreditAccount account) {
        List<CreditInstallment> accountInstallments = creditInstallmentRepository
                .findByAccount_IdOrderByInstallmentNumber(account.getId());
        List<CreditPayment> payments = creditPaymentRepository.findByAccount_IdOrderByPaidAtAscIdAsc(account.getId());
        return replayAccountState(account, accountInstallments, payments, false).allocations();
    }

    private ReplayResult replayAccountState(CreditAccount account,
                                            List<CreditInstallment> sourceInstallments,
                                            List<CreditPayment> payments,
                                            boolean updatePayments) {
        List<CreditInstallment> replayInstallments = sourceInstallments.stream()
                .map(this::copyInstallmentForReplay)
                .sorted(Comparator.comparing(CreditInstallment::getInstallmentNumber)
                        .thenComparing(CreditInstallment::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        Map<Long, CreditInstallment> replayInstallmentsById = new HashMap<>();
        Set<Long> disabledInstallmentIds = new HashSet<>();
        for (CreditInstallment installment : replayInstallments) {
            replayInstallmentsById.put(installment.getId(), installment);
            installment.setVoided(false);
            installment.setStatus(InstallmentStatus.PENDING);
            installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            installment.setPaidAt(null);
            if (installment.getRestoredFromInstallmentId() != null) {
                disabledInstallmentIds.add(installment.getId());
            }
        }

        List<PaymentAllocation> allocations = new ArrayList<>();
        for (CreditPayment payment : payments) {
            if (payment.isReversal()) {
                CreditInstallment reversalTarget = applyReversalToInstallments(
                        replayInstallments,
                        replayInstallments,
                        payment
                );
                applyReversalToAllocations(allocations, payment, reversalTarget);
                updateReplayAvailability(disabledInstallmentIds, replayInstallmentsById, reversalTarget);
                if (updatePayments && (payment.getAllocationSummary() == null || payment.getAllocationSummary().isBlank())) {
                    payment.setAllocationSummary("Reversion de anulacion");
                }
                continue;
            }
            PaymentApplicationResult result = applyPaymentToInstallments(
                    account,
                    replayEligibleInstallments(replayInstallments, disabledInstallmentIds),
                    payment.getAmount(),
                    payment.getPaymentMethod() == null ? PaymentCollectionMethod.BANK : payment.getPaymentMethod(),
                    payment.getPaidAt(),
                    payment.getId(),
                    allocations
            );
            if (updatePayments) {
                payment.setImpactAmount(result.impactAmount().setScale(2, RoundingMode.HALF_UP));
                payment.setAllocationSummary(buildAllocationSummary(result.paymentReferences()));
            }
        }
        return new ReplayResult(replayInstallmentsById, allocations);
    }

    private List<CreditPayment> buildReversalPayments(CreditInstallment originalInstallment,
                                                      CreditInstallment restoredInstallment,
                                                      List<PaymentAllocation> impactedAllocations,
                                                      String voidedBy,
                                                      String reason) {
        List<CreditPayment> reversalPayments = new ArrayList<>();
        for (PaymentAllocation allocation : impactedAllocations) {
            if (allocation.impactAmount().compareTo(BigDecimal.ZERO) <= 0
                    && allocation.collectedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean selectedInstallmentAllocation = originalInstallment.getId().equals(allocation.installmentId());
            CreditPayment reversalPayment = new CreditPayment();
            reversalPayment.setAccount(originalInstallment.getAccount());
            reversalPayment.setAmount(allocation.collectedAmount().negate().setScale(2, RoundingMode.HALF_UP));
            reversalPayment.setImpactAmount(allocation.impactAmount().negate().setScale(2, RoundingMode.HALF_UP));
            reversalPayment.setPaidAt(LocalDate.now());
            reversalPayment.setRegisteredBy(trimToNull(voidedBy));
            reversalPayment.setPaymentMethod(allocation.paymentMethod());
            reversalPayment.setReversal(true);
            reversalPayment.setReversalOfPaymentId(allocation.paymentId());
            reversalPayment.setTargetInstallmentId(
                    selectedInstallmentAllocation ? restoredInstallment.getId() : allocation.installmentId()
            );
            reversalPayment.setReversalReason(trimToNull(reason));
            if (selectedInstallmentAllocation) {
                reversalPayment.setAllocationSummary(
                        "Anulacion cuota #" + originalInstallment.getInstallmentNumber()
                                + " -> restaura cuota reimpactable #"
                                + restoredInstallment.getInstallmentNumber()
                );
            } else {
                reversalPayment.setAllocationSummary(
                        "Anulacion cuota #" + originalInstallment.getInstallmentNumber()
                                + " -> revierte saldo aplicado en cuota #"
                                + allocation.installmentNumber()
                );
            }
            reversalPayments.add(reversalPayment);
        }
        return reversalPayments;
    }

    private CreditInstallment applyReversalToInstallments(List<CreditInstallment> activeInstallments,
                                                          List<CreditInstallment> allInstallments,
                                                          CreditPayment payment) {
        Long targetInstallmentId = payment.getTargetInstallmentId();
        if (targetInstallmentId == null) {
            throw new InvalidSaleException("La reversa no tiene cuota de destino");
        }
        CreditInstallment targetInstallment = resolveReversalTargetInstallment(
                activeInstallments,
                allInstallments,
                targetInstallmentId
        );

        BigDecimal currentPaid = targetInstallment.getPaidAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : targetInstallment.getPaidAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal reversalImpact = payment.getImpactAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : payment.getImpactAmount().abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal recalculatedPaid = currentPaid.subtract(reversalImpact).setScale(2, RoundingMode.HALF_UP);
        if (recalculatedPaid.compareTo(BigDecimal.ZERO) < 0) {
            recalculatedPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        targetInstallment.setPaidAmount(recalculatedPaid);
        if (recalculatedPaid.compareTo(BigDecimal.ZERO) == 0) {
            targetInstallment.setStatus(InstallmentStatus.PENDING);
            targetInstallment.setPaidAt(null);
        } else if (recalculatedPaid.compareTo(targetInstallment.getAmount().setScale(2, RoundingMode.HALF_UP)) >= 0) {
            targetInstallment.setStatus(InstallmentStatus.PAID);
        } else {
            targetInstallment.setStatus(InstallmentStatus.PARTIAL);
            targetInstallment.setPaidAt(null);
        }
        return targetInstallment;
    }

    private CreditInstallment resolveReversalTargetInstallment(List<CreditInstallment> activeInstallments,
                                                               List<CreditInstallment> allInstallments,
                                                               Long targetInstallmentId) {
        for (CreditInstallment installment : allInstallments) {
            if (targetInstallmentId.equals(installment.getId())) {
                return installment;
            }
        }

        for (CreditInstallment installment : activeInstallments) {
            if (targetInstallmentId.equals(installment.getId())) {
                return installment;
            }
        }

        Map<Long, List<CreditInstallment>> restoredChildrenByInstallmentId = new HashMap<>();
        for (CreditInstallment installment : allInstallments) {
            Long restoredFromInstallmentId = installment.getRestoredFromInstallmentId();
            if (restoredFromInstallmentId == null) {
                continue;
            }
            restoredChildrenByInstallmentId
                    .computeIfAbsent(restoredFromInstallmentId, key -> new ArrayList<>())
                    .add(installment);
        }

        Long currentInstallmentId = targetInstallmentId;
        while (currentInstallmentId != null) {
            List<CreditInstallment> restoredChildren = restoredChildrenByInstallmentId.get(currentInstallmentId);
            if (restoredChildren == null || restoredChildren.isEmpty()) {
                break;
            }

            CreditInstallment latestChild = null;
            for (CreditInstallment restoredChild : restoredChildren) {
                if (isActiveInstallment(restoredChild)) {
                    for (CreditInstallment activeInstallment : activeInstallments) {
                        if (restoredChild.getId().equals(activeInstallment.getId())) {
                            return activeInstallment;
                        }
                    }
                }
                if (latestChild == null || compareInstallmentIds(restoredChild.getId(), latestChild.getId()) > 0) {
                    latestChild = restoredChild;
                }
            }

            currentInstallmentId = latestChild != null ? latestChild.getId() : null;
        }

        throw new InvalidSaleException("No se encontro la cuota restaurada para recalcular la anulacion");
    }

    private void applyReversalToAllocations(List<PaymentAllocation> allocations,
                                            CreditPayment payment,
                                            CreditInstallment targetInstallment) {
        Long reversalOfPaymentId = payment.getReversalOfPaymentId();
        if (reversalOfPaymentId == null || targetInstallment == null) {
            return;
        }

        BigDecimal remainingCollected = payment.getAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : payment.getAmount().abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingImpact = payment.getImpactAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : payment.getImpactAmount().abs().setScale(2, RoundingMode.HALF_UP);

        for (int index = 0; index < allocations.size(); index++) {
            PaymentAllocation allocation = allocations.get(index);
            if (!reversalOfPaymentId.equals(allocation.paymentId())
                    || !targetInstallment.getId().equals(allocation.installmentId())) {
                continue;
            }
            if (remainingCollected.compareTo(BigDecimal.ZERO) <= 0
                    && remainingImpact.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal collectedToReverse = allocation.collectedAmount().min(remainingCollected).setScale(2, RoundingMode.HALF_UP);
            BigDecimal impactToReverse = allocation.impactAmount().min(remainingImpact).setScale(2, RoundingMode.HALF_UP);
            allocations.set(
                    index,
                    new PaymentAllocation(
                            allocation.paymentId(),
                            allocation.installmentId(),
                            allocation.installmentNumber(),
                            allocation.paymentMethod(),
                            allocation.carriedForward(),
                            allocation.collectedAmount().subtract(collectedToReverse).setScale(2, RoundingMode.HALF_UP),
                            allocation.impactAmount().subtract(impactToReverse).setScale(2, RoundingMode.HALF_UP)
                    )
            );
            remainingCollected = remainingCollected.subtract(collectedToReverse).setScale(2, RoundingMode.HALF_UP);
            remainingImpact = remainingImpact.subtract(impactToReverse).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private boolean hasCurrentPaymentImpact(List<PaymentAllocation> impactedAllocations) {
        if (impactedAllocations == null || impactedAllocations.isEmpty()) {
            return false;
        }
        return impactedAllocations.stream().anyMatch(allocation ->
                (allocation.impactAmount() != null && allocation.impactAmount().compareTo(BigDecimal.ZERO) > 0)
                        || (allocation.collectedAmount() != null && allocation.collectedAmount().compareTo(BigDecimal.ZERO) > 0)
        );
    }

    private List<PaymentAllocation> resolveImpactedAllocationsForVoid(CreditInstallment installment,
                                                                      List<PaymentAllocation> currentAllocations,
                                                                      List<PaymentAllocation> installmentAllocations) {
        if (currentAllocations == null || currentAllocations.isEmpty()
                || installmentAllocations == null || installmentAllocations.isEmpty()) {
            return List.of();
        }
        Set<Long> paymentIdsToReverse = installmentAllocations.stream()
                .map(PaymentAllocation::paymentId)
                .filter(paymentId -> paymentId != null)
                .collect(java.util.stream.Collectors.toSet());
        int selectedInstallmentNumber = installment.getInstallmentNumber() == null ? Integer.MIN_VALUE : installment.getInstallmentNumber();
        return currentAllocations.stream()
                .filter(allocation -> paymentIdsToReverse.contains(allocation.paymentId()))
                .filter(allocation -> allocation.installmentId() != null)
                .filter(allocation -> installment.getId().equals(allocation.installmentId())
                        || (allocation.installmentNumber() != null && allocation.installmentNumber() > selectedInstallmentNumber))
                .toList();
    }

    private int compareInstallmentIds(Long leftId, Long rightId) {
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return -1;
        }
        if (rightId == null) {
            return 1;
        }
        return leftId.compareTo(rightId);
    }

    private boolean isActiveInstallment(CreditInstallment installment) {
        return !installment.isVoided() && installment.getStatus() != InstallmentStatus.VOID;
    }

    private List<CreditInstallment> replayEligibleInstallments(List<CreditInstallment> activeInstallments,
                                                               Set<Long> disabledInstallmentIds) {
        return activeInstallments.stream()
                .filter(installment -> installment.getId() != null && !disabledInstallmentIds.contains(installment.getId()))
                .toList();
    }

    private void updateReplayAvailability(Set<Long> disabledInstallmentIds,
                                          Map<Long, CreditInstallment> replayInstallmentsById,
                                          CreditInstallment reversalTarget) {
        if (reversalTarget == null) {
            return;
        }
        if (reversalTarget.getRestoredFromInstallmentId() != null) {
            disabledInstallmentIds.remove(reversalTarget.getId());
            disabledInstallmentIds.add(reversalTarget.getRestoredFromInstallmentId());
            return;
        }
        CreditInstallment restoredChild = replayInstallmentsById.values().stream()
                .filter(installment -> reversalTarget.getId().equals(installment.getRestoredFromInstallmentId()))
                .findFirst()
                .orElse(null);
        if (restoredChild != null) {
            disabledInstallmentIds.remove(restoredChild.getId());
            disabledInstallmentIds.add(reversalTarget.getId());
        }
    }

    private CreditInstallment copyInstallmentForReplay(CreditInstallment source) {
        CreditInstallment copy = new CreditInstallment();
        copy.setId(source.getId());
        copy.setAccount(source.getAccount());
        copy.setInstallmentNumber(source.getInstallmentNumber());
        copy.setDueDate(source.getDueDate());
        copy.setAmount(source.getAmount().setScale(2, RoundingMode.HALF_UP));
        copy.setPaidAmount(
                source.getPaidAmount() == null
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : source.getPaidAmount().setScale(2, RoundingMode.HALF_UP)
        );
        copy.setStatus(source.getStatus());
        copy.setPaidAt(source.getPaidAt());
        copy.setVoided(source.isVoided());
        copy.setRestoredFromInstallmentId(source.getRestoredFromInstallmentId());
        return copy;
    }

    @Override
    public long countMoroseClients() {
        return creditInstallmentRepository
                .findByStatusInAndDueDateBefore(
                        List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL),
                        LocalDate.now()
                )
                .stream()
                .filter(inst -> isVisibleAccount(inst.getAccount()))
                .map(inst -> inst.getAccount().getClient().getId())
                .distinct()
                .count();
    }

    @Override
    public List<MorositySummary> getMorosity(MorosityLevel levelFilter) {
        return getMorosity(levelFilter, null);
    }

    @Override
    public List<MorositySummary> getMorosity(MorosityLevel levelFilter, Long requestedSellerId) {
        LocalDate today = LocalDate.now();
        boolean admin = isCurrentUserAdmin();
        // FILTRO VENDEDOR BACKEND
        Long sellerId = admin ? normalizeFilterId(requestedSellerId) : currentUserId();
        List<CreditAccount> accounts = admin
                ? creditAccountRepository.findAll().stream()
                    .filter(this::isVisibleAccount)
                    .toList()
                : sellerId == null ? List.of() : creditAccountRepository.findBySale_Seller_Id(sellerId).stream()
                    .filter(this::isVisibleAccount)
                    .toList();
        if (admin && sellerId != null) {
            accounts = accounts.stream()
                    .filter(account -> account.getSale() != null
                            && account.getSale().getSeller() != null
                            && sellerId.equals(account.getSale().getSeller().getId()))
                    .toList();
        }

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
                    if (!isVisibleAccount(installment.getAccount())) {
                        return;
                    }
                    if (!admin) {
                        Long installmentSellerId = installment.getAccount().getSale() == null
                                || installment.getAccount().getSale().getSeller() == null
                                ? null
                                : installment.getAccount().getSale().getSeller().getId();
                        if (sellerId == null || !sellerId.equals(installmentSellerId)) {
                            return;
                        }
                    }
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

    private Long normalizeFilterId(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private CreditAccount requireVisibleAccount(CreditAccount account) {
        if (!isVisibleAccount(account)) {
            throw new InvalidSaleException("La cuenta vinculada a una venta anulada ya no esta disponible");
        }
        return account;
    }

    private boolean isVisibleAccount(CreditAccount account) {
        return account != null
                && account.getStatus() != AccountStatus.VOID
                && (account.getSale() == null || account.getSale().getStatus() != SaleStatus.VOID);
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

    private record PaymentApplicationResult(BigDecimal impactAmount,
                                            Map<Integer, String> paymentReferences,
                                            List<PaymentAllocation> allocations) {
    }

    private record ReplayResult(Map<Long, CreditInstallment> installmentsById,
                                List<PaymentAllocation> allocations) {
    }

    private record PaymentAllocation(Long paymentId,
                                     Long installmentId,
                                     Integer installmentNumber,
                                     PaymentCollectionMethod paymentMethod,
                                     boolean carriedForward,
                                     BigDecimal collectedAmount,
                                     BigDecimal impactAmount) {
    }

    private String buildAllocationSummary(Map<Integer, String> paymentReferences) {
        if (paymentReferences == null || paymentReferences.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        int totalReferences = paymentReferences.size();
        int includedReferences = 0;
        for (String reference : paymentReferences.values()) {
            if (reference == null || reference.isBlank()) {
                continue;
            }
            String separator = summary.length() == 0 ? "" : " | ";
            if (summary.length() + separator.length() + reference.length() > ALLOCATION_SUMMARY_MAX_LENGTH) {
                break;
            }
            summary.append(separator).append(reference);
            includedReferences++;
        }
        if (includedReferences < totalReferences) {
            String hiddenReferences = " | +" + (totalReferences - includedReferences) + " cuota(s)";
            if (summary.length() + hiddenReferences.length() <= ALLOCATION_SUMMARY_MAX_LENGTH) {
                summary.append(hiddenReferences);
            }
        }
        return summary.toString();
    }

    private String buildPaymentReference(CreditAccount account,
                                         CreditInstallment installment,
                                         PaymentCollectionMethod paymentMethod,
                                         LocalDate paidAt,
                                         BigDecimal collectedApplied,
                                         BigDecimal remainingAfterAllocation,
                                         boolean carriedForward,
                                         boolean fullPayment) {
        boolean cashValue = CreditPaymentPricingSupport.usesCashValue(
                paymentMethod,
                account.getPaymentFrequency(),
                installment.getDueDate(),
                paidAt
        );
        StringBuilder reference = new StringBuilder("Cuota #")
                .append(installment.getInstallmentNumber())
                .append(": ");
        if (carriedForward) {
            reference.append("recibe ")
                    .append(formatMoney(collectedApplied))
                    .append(" por saldo a favor");
        } else {
            reference.append("se aplican ")
                    .append(formatMoney(collectedApplied));
        }
        if (fullPayment) {
            reference.append(" y queda saldada");
        } else {
            reference.append(" y quedan ")
                    .append(formatMoney(remainingAfterAllocation))
                    .append(" pendientes");
        }
        reference.append(cashValue ? " (valor contado)" : " (valor financiado)");
        return reference.toString();
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal normalizedAmount = amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("es", "AR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00", symbols);
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return "$ " + decimalFormat.format(normalizedAmount);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

