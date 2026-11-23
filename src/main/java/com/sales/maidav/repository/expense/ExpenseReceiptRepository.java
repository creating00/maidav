package com.sales.maidav.repository.expense;

import com.sales.maidav.model.expense.ExpenseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseReceiptRepository extends JpaRepository<ExpenseReceipt, Long> {
}
