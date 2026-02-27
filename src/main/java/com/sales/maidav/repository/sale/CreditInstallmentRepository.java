package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
