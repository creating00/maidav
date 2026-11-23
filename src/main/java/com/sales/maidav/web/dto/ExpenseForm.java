package com.sales.maidav.web.dto;

import com.sales.maidav.model.expense.ExpenseCategory;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private String description;
    private BigDecimal amount;
    private PaymentCollectionMethod paymentMethod;
    private String operationKey;
}
