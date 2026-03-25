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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        List<PaymentAllocation> impactedAllocations = collectCurrentAllocations(installment.getAccount()).stream()
                .filter(allocation -> installmentId.equals(allocation.installmentId()))
                .toList();

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
                    account.getPaymentFrequency(),
                    installment.getDueDate(),
                    paidAt
            );
            BigDecimal collectedApplied = remainingInput.min(collectedNeeded).setScale(2, RoundingMode.HALF_UP);
            BigDecimal allocationImpact = CreditPaymentPricingSupport.resolveImpactAmount(
                    financedRemaining,
                    collectedApplied,
                    cashRecargo,
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
            remainingInput = remainingInput.subtract(collectedApplied).setScale(2, RoundingMode.HALF_UP);
            impactAmount = impactAmount.add(allocationImpact).setScale(2, RoundingMode.HALF_UP);
            paymentReferences.put(
                    installment.getInstallmentNumber(),
                    buildPaymentReference(account, installment, paidAt, installment.getStatus() == InstallmentStatus.PAID)
            );
            allocations.add(new PaymentAllocation(
                    paymentId,
                    installment.getId(),
                    installment.getInstallmentNumber(),
                    paymentMethod,
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

        for (CreditInstallment installment : installments) {
            if (installment.isVoided() || installment.getStatus() == InstallmentStatus.VOID) {
                installment.setVoided(true);
                installment.setStatus(InstallmentStatus.VOID);
                continue;
            }
            installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            installment.setStatus(InstallmentStatus.PENDING);
            installment.setPaidAt(null);
        }

        List<CreditInstallment> activeInstallments = installments.stream()
                .filter(installment -> !installment.isVoided() && installment.getStatus() != InstallmentStatus.VOID)
                .toList();
        for (CreditPayment payment : payments) {
            if (payment.isReversal()) {
                // FIX ANULACION CUOTA
                // REVERSION IMPACTO ECONOMICO
                applyReversalToInstallments(activeInstallments, payment);
                if (payment.getAllocationSummary() == null || payment.getAllocationSummary().isBlank()) {
                    payment.setAllocationSummary("Reversion de anulacion");
                }
                continue;
            }

            PaymentApplicationResult result = applyPaymentToInstallments(
                    account,
                    activeInstallments,
                    payment.getAmount(),
                    payment.getPaymentMethod() == null ? PaymentCollectionMethod.BANK : payment.getPaymentMethod(),
                    payment.getPaidAt(),
                    payment.getId(),
                    new ArrayList<>()
            );
            payment.setImpactAmount(result.impactAmount().setScale(2, RoundingMode.HALF_UP));
            payment.setAllocationSummary(buildAllocationSummary(result.paymentReferences()));
        }

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
        List<CreditInstallment> replayInstallments = creditInstallmentRepository
                .findByAccount_IdOrderByInstallmentNumber(account.getId())
                .stream()
                .filter(installment -> !installment.isVoided() && installment.getStatus() != InstallmentStatus.VOID)
                .map(this::copyInstallmentForReplay)
                .toList();
        List<CreditPayment> payments = creditPaymentRepository.findByAccount_IdOrderByPaidAtAscIdAsc(account.getId());
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (CreditInstallment installment : replayInstallments) {
            installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            installment.setStatus(InstallmentStatus.PENDING);
            installment.setPaidAt(null);
        }

        for (CreditPayment payment : payments) {
            if (payment.isReversal()) {
                applyReversalToInstallments(replayInstallments, payment);
                continue;
            }
            applyPaymentToInstallments(
                    account,
                    replayInstallments,
                    payment.getAmount(),
                    payment.getPaymentMethod() == null ? PaymentCollectionMethod.BANK : payment.getPaymentMethod(),
                    payment.getPaidAt(),
                    payment.getId(),
                    allocations
            );
        }
        return allocations;
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
            CreditPayment reversalPayment = new CreditPayment();
            reversalPayment.setAccount(originalInstallment.getAccount());
            reversalPayment.setAmount(allocation.collectedAmount().negate().setScale(2, RoundingMode.HALF_UP));
            reversalPayment.setImpactAmount(allocation.impactAmount().negate().setScale(2, RoundingMode.HALF_UP));
            reversalPayment.setPaidAt(LocalDate.now());
            reversalPayment.setRegisteredBy(trimToNull(voidedBy));
            reversalPayment.setPaymentMethod(allocation.paymentMethod());
            reversalPayment.setReversal(true);
            reversalPayment.setReversalOfPaymentId(allocation.paymentId());
            reversalPayment.setTargetInstallmentId(restoredInstallment.getId());
            reversalPayment.setReversalReason(trimToNull(reason));
            reversalPayment.setAllocationSummary(
                    "Anulacion cuota #" + originalInstallment.getInstallmentNumber()
                            + " -> restaura cuota reimpactable #"
                            + restoredInstallment.getInstallmentNumber()
            );
            reversalPayments.add(reversalPayment);
        }
        return reversalPayments;
    }

    private void applyReversalToInstallments(List<CreditInstallment> installments, CreditPayment payment) {
        Long targetInstallmentId = payment.getTargetInstallmentId();
        if (targetInstallmentId == null) {
            throw new InvalidSaleException("La reversa no tiene cuota de destino");
        }
        CreditInstallment targetInstallment = installments.stream()
                .filter(installment -> targetInstallmentId.equals(installment.getId()))
                .findFirst()
                .orElseThrow(() -> new InvalidSaleException("No se encontro la cuota restaurada para recalcular la anulacion"));

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
                ? creditAccountRepository.findAll()
                : sellerId == null ? List.of() : creditAccountRepository.findBySale_Seller_Id(sellerId);
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

    private record PaymentAllocation(Long paymentId,
                                     Long installmentId,
                                     Integer installmentNumber,
                                     PaymentCollectionMethod paymentMethod,
                                     BigDecimal collectedAmount,
                                     BigDecimal impactAmount) {
    }

    private String buildAllocationSummary(Map<Integer, String> paymentReferences) {
        if (paymentReferences == null || paymentReferences.isEmpty()) {
            return null;
        }
        return String.join(" | ", paymentReferences.values());
    }

    private String buildPaymentReference(CreditAccount account,
                                         CreditInstallment installment,
                                         LocalDate paidAt,
                                         boolean fullPayment) {
        boolean cashValue = CreditPaymentPricingSupport.usesCashValue(
                account.getPaymentFrequency(),
                installment.getDueDate(),
                paidAt
        );
        if (cashValue) {
            // PAGO EN FECHA COMO CONTADO
            return "Cuota #" + installment.getInstallmentNumber()
                    + (fullPayment ? " total" : " parcial")
                    + " (valor contado)";
        }
        // PAGO FUERA DE FECHA COMO FINANCIADO
        return "Cuota #" + installment.getInstallmentNumber()
                + (fullPayment ? " total" : " parcial")
                + " (valor financiado)";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

