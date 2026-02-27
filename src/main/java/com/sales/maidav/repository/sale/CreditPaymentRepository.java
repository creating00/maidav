package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditPaymentRepository extends JpaRepository<CreditPayment, Long> {
    List<CreditPayment> findByAccount_IdOrderByPaidAtDesc(Long accountId);
}
