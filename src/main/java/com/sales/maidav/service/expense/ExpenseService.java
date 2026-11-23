package com.sales.maidav.service.expense;

import com.sales.maidav.model.expense.*;
import com.sales.maidav.web.dto.ExpenseForm;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpenseService {
    Expense create(ExpenseForm form, Long userId, MultipartFile receipt);
    Expense findById(Long id);
    ExpenseReceipt findReceipt(Long id);
    Page<Expense> findPage(LocalDate from, LocalDate to, ExpenseCategory category, BigDecimal minAmount, BigDecimal maxAmount, int page);
}
