package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SaleService {
    List<Sale> findAll();
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
    void voidSale(Long id);
    long countActive();
    long countToday();
}
