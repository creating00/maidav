package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.CreditAccount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CreditAccountService {
    List<CreditAccount> findAll();
    CreditAccount findById(Long id);
    CreditAccount findBySaleId(Long saleId);
    void registerPayment(Long accountId, BigDecimal amount);
    void registerPayment(Long accountId, BigDecimal amount, java.util.List<Long> installmentIds);
    void updatePayment(Long accountId, Long paymentId, BigDecimal amount, LocalDate paidAt);
    void updateInstallmentDueDate(Long accountId, Long installmentId, LocalDate dueDate);
    long countMoroseClients();
    java.util.List<MorositySummary> getMorosity(MorosityLevel levelFilter);
}
