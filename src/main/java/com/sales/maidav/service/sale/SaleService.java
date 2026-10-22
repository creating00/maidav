package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleSellerChange;
import com.sales.maidav.model.user.User;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SaleService {
    List<Sale> findAll();
    Page<Sale> findPage(String q, PaymentType paymentType, boolean showVoided, int page, int pageSize);
    Sale findById(Long id);
    Sale createSale(Client client,
                    User seller,
                    PaymentType paymentType,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<SaleItemInput> items);
    Sale createSale(Client client,
                    User seller,
                    PaymentType paymentType,
                    PaymentCollectionMethod paymentCollectionMethod,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<BigDecimal> manualInstallmentAmounts,
                    List<BigDecimal> manualCashInstallmentAmounts,
                    List<SaleItemInput> items);
    Sale createSale(Client client,
                    User seller,
                    PaymentType paymentType,
                    PaymentCollectionMethod paymentCollectionMethod,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<BigDecimal> manualInstallmentAmounts,
                    List<BigDecimal> manualCashInstallmentAmounts,
                    List<SaleItemInput> items,
                    Long idempotencyUserId,
                    String idempotencyKey);
    Sale createSale(Client client,
                    User seller,
                    PaymentType paymentType,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<BigDecimal> manualInstallmentAmounts,
                    List<SaleItemInput> items);
    Sale updateSale(Long saleId,
                    Client client,
                    User seller,
                    PaymentType paymentType,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<SaleItemInput> items);
    Sale updateSale(Long saleId,
                    Client client,
                    User seller,
                    PaymentType paymentType,
                    PaymentCollectionMethod paymentCollectionMethod,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<BigDecimal> manualInstallmentAmounts,
                    List<BigDecimal> manualCashInstallmentAmounts,
                    List<SaleItemInput> items);
    Sale updateSale(Long saleId,
                    Client client,
                    User seller,
                    PaymentType paymentType,
                    LocalDate saleDate,
                    LocalDate firstDueDate,
                    PaymentFrequency paymentFrequency,
                    List<String> dueDays,
                    BigDecimal discountAmount,
                    Integer weeksCount,
                    List<BigDecimal> manualInstallmentAmounts,
                    List<SaleItemInput> items);
    Sale changeSeller(Long saleId, User newSeller, User changedBy);
    List<SaleSellerChange> findSellerChanges(Long saleId);
    void voidSale(Long id);
    long countActive();
    long countToday();
}
