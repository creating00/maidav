package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.*;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditAccountServiceImpl implements CreditAccountService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;

    @Override
    public List<CreditAccount> findAll() {
        return creditAccountRepository.findAll();
    }

    @Override
    public CreditAccount findById(Long id) {
        return creditAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
    }

    @Override
    public CreditAccount findBySaleId(Long saleId) {
        return creditAccountRepository.findBySale_Id(saleId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount) {
        registerPayment(accountId, amount, null);
    }

    @Override
    public void registerPayment(Long accountId, BigDecimal amount, List<Long> installmentIds) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSaleException("El pago debe ser mayor a cero");
        }
        CreditAccount account = findById(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidSaleException("La cuenta ya esta saldada");
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El pago supera el saldo");
        }

        CreditPayment payment = new CreditPayment();
        payment.setAccount(account);
        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(LocalDate.now());
        creditPaymentRepository.save(payment);

        List<CreditInstallment> installments = resolveInstallments(accountId, installmentIds);
        if (installmentIds != null && !installmentIds.isEmpty()) {
            BigDecimal selectableRemaining = BigDecimal.ZERO;
            for (CreditInstallment installment : installments) {
                if (installment.getStatus() == InstallmentStatus.PAID) {
                    continue;
                }
                BigDecimal paidAmount = installment.getPaidAmount() == null ? BigDecimal.ZERO : installment.getPaidAmount();
                BigDecimal installmentRemaining = installment.getAmount().subtract(paidAmount);
                if (installmentRemaining.compareTo(BigDecimal.ZERO) > 0) {
                    selectableRemaining = selectableRemaining.add(installmentRemaining);
                }
            }
            if (amount.compareTo(selectableRemaining) > 0) {
                throw new InvalidSaleException("El pago supera el saldo de las cuotas seleccionadas");
            }
        }

        BigDecimal remaining = amount;

        for (CreditInstallment installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (installment.getStatus() == InstallmentStatus.PAID) {
                continue;
            }
            BigDecimal paidAmount = installment.getPaidAmount() == null ? BigDecimal.ZERO : installment.getPaidAmount();
            BigDecimal installmentRemaining = installment.getAmount().subtract(paidAmount);
            if (installmentRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                installment.setStatus(InstallmentStatus.PAID);
                continue;
            }
            if (remaining.compareTo(installmentRemaining) >= 0) {
                installment.setPaidAmount(installment.getAmount());
                installment.setStatus(InstallmentStatus.PAID);
                installment.setPaidAt(LocalDate.now());
                remaining = remaining.subtract(installmentRemaining);
            } else {
                installment.setPaidAmount(paidAmount.add(remaining).setScale(2, RoundingMode.HALF_UP));
                installment.setStatus(InstallmentStatus.PARTIAL);
                remaining = BigDecimal.ZERO;
            }
        }

        account.setBalance(newBalance.setScale(2, RoundingMode.HALF_UP));
        if (account.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            account.setStatus(AccountStatus.CLOSED);
        }
    }

    @Override
    public void updatePayment(Long accountId, Long paymentId, BigDecimal amount, LocalDate paidAt) {
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

        BigDecimal totalPaid = creditPaymentRepository.findByAccount_IdOrderByPaidAtAscIdAsc(accountId).stream()
                .map(CreditPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaid.compareTo(account.getTotalAmount()) > 0) {
            throw new InvalidSaleException("La suma de pagos no puede superar el total de la cuenta");
        }

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
            BigDecimal remaining = payment.getAmount();
            for (CreditInstallment installment : installments) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal installmentRemaining = installment.getAmount().subtract(installment.getPaidAmount());
                if (installmentRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (remaining.compareTo(installmentRemaining) >= 0) {
                    installment.setPaidAmount(installment.getAmount().setScale(2, RoundingMode.HALF_UP));
                    installment.setStatus(InstallmentStatus.PAID);
                    installment.setPaidAt(payment.getPaidAt());
                    remaining = remaining.subtract(installmentRemaining);
                } else {
                    installment.setPaidAmount(
                            installment.getPaidAmount().add(remaining).setScale(2, RoundingMode.HALF_UP)
                    );
                    installment.setStatus(InstallmentStatus.PARTIAL);
                    remaining = BigDecimal.ZERO;
                }
            }
        }

        BigDecimal totalPaid = payments.stream()
                .map(CreditPayment::getAmount)
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
}
