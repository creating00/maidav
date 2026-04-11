package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditInstallmentRepository extends JpaRepository<CreditInstallment, Long> {
    List<CreditInstallment> findByAccount_IdOrderByInstallmentNumber(Long accountId);
    List<CreditInstallment> findByAccount_IdAndIdInOrderByInstallmentNumber(Long accountId, List<Long> ids);
    List<CreditInstallment> findByStatusAndDueDateBefore(InstallmentStatus status, LocalDate date);
    List<CreditInstallment> findByStatusInAndDueDateBefore(List<InstallmentStatus> statuses, LocalDate date);
    Optional<CreditInstallment> findFirstByAccount_IdAndStatusOrderByInstallmentNumberAsc(
            Long accountId,
            InstallmentStatus status
    );
    Optional<CreditInstallment> findFirstByAccount_IdAndStatusNotOrderByInstallmentNumberAsc(
            Long accountId,
            InstallmentStatus status
    );

    @Query("""
            select coalesce(sum(installment.amount), 0)
            from CreditInstallment installment
            where installment.dueDate <= :cutoffDate
              and installment.status <> com.sales.maidav.model.sale.InstallmentStatus.VOID
              and installment.account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
              and (:sellerId is null or installment.account.sale.seller.id = :sellerId)
              and (:zoneId is null or installment.account.client.zone.id = :zoneId)
            """)
    BigDecimal sumScheduledAmountDueUpTo(@Param("cutoffDate") LocalDate cutoffDate,
                                         @Param("sellerId") Long sellerId,
                                         @Param("zoneId") Long zoneId);

    @Query("""
            select coalesce(sum(
                case
                    when (installment.amount - coalesce(installment.paidAmount, 0)) > 0
                        then (installment.amount - coalesce(installment.paidAmount, 0))
                    else 0
                end
            ), 0)
            from CreditInstallment installment
            where installment.dueDate < :cutoffDate
              and installment.status in :pendingStatuses
              and installment.account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
              and (:sellerId is null or installment.account.sale.seller.id = :sellerId)
              and (:zoneId is null or installment.account.client.zone.id = :zoneId)
            """)
    BigDecimal sumOverdueOutstandingByFilters(@Param("cutoffDate") LocalDate cutoffDate,
                                              @Param("pendingStatuses") List<InstallmentStatus> pendingStatuses,
                                              @Param("sellerId") Long sellerId,
                                              @Param("zoneId") Long zoneId);
}
