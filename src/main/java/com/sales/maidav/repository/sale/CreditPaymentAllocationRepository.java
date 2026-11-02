package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditPaymentAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditPaymentAllocationRepository extends JpaRepository<CreditPaymentAllocation, Long> {
    @EntityGraph(attributePaths = {"payment", "installment"})
    List<CreditPaymentAllocation> findByAccount_IdIn(List<Long> accountIds);

    List<CreditPaymentAllocation> findByAccount_Id(Long accountId);

    void deleteByAccount_Id(Long accountId);
}
