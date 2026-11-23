package com.sales.maidav.model.expense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expense_receipts")
@Getter
@NoArgsConstructor
public class ExpenseReceipt {
    @Id
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(nullable = false)
    private byte[] content;

    public ExpenseReceipt(Long expenseId, byte[] content) {
        this.expenseId = expenseId;
        this.content = content;
    }
}
