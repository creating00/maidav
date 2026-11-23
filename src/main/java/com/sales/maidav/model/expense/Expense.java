package com.sales.maidav.model.expense;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Getter
@Setter
public class Expense extends BaseEntity {
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentCollectionMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_id", nullable = false)
    private User registeredBy;

    @Column(name = "receipt_name", length = 200)
    private String receiptName;

    @Column(name = "receipt_content_type", length = 30)
    private String receiptContentType;

    @Column(name = "receipt_size")
    private Integer receiptSize;
}
