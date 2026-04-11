package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditPaymentRepository extends JpaRepository<CreditPayment, Long> {
    List<CreditPayment> findByAccount_IdOrderByPaidAtDesc(Long accountId);
    List<CreditPayment> findByAccount_IdOrderByPaidAtDescIdDesc(Long accountId);
    List<CreditPayment> findByAccount_IdOrderByPaidAtAscIdAsc(Long accountId);
    Optional<CreditPayment> findByAccount_IdAndOperationToken(Long accountId, String operationToken);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from CreditPayment payment
            where payment.paidAt <= :cutoffDate
              and payment.account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
              and (:sellerId is null or payment.account.sale.seller.id = :sellerId)
              and (:zoneId is null or payment.account.client.zone.id = :zoneId)
            """)
    BigDecimal sumPaidAmountUpTo(@Param("cutoffDate") LocalDate cutoffDate,
                                 @Param("sellerId") Long sellerId,
                                 @Param("zoneId") Long zoneId);
}

