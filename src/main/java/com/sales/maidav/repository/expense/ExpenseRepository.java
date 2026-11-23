package com.sales.maidav.repository.expense;

import com.sales.maidav.model.expense.Expense;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {
    @Override
    @EntityGraph(attributePaths = "registeredBy")
    Optional<Expense> findById(Long id);
}
