package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.PaymentCollectionMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CreditAccountService {
    List<CreditAccount> findAll();
    CreditAccount findById(Long id);
    CreditAccount findBySaleId(Long saleId);
    void registerPayment(Long accountId, BigDecimal amount);
    void registerPayment(Long accountId, BigDecimal amount, java.util.List<Long> installmentIds);
    void registerPayment(Long accountId, BigDecimal amount, java.util.List<Long> installmentIds, String registeredBy,
                         PaymentCollectionMethod paymentMethod);
    void updatePayment(Long accountId, Long paymentId, BigDecimal amount, LocalDate paidAt,
                       PaymentCollectionMethod paymentMethod);
    void updateInstallmentDueDate(Long accountId, Long installmentId, LocalDate dueDate);
    long countMoroseClients();
    java.util.List<MorositySummary> getMorosity(MorosityLevel levelFilter);
}
