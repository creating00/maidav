package com.sales.maidav.service.purchase;

import com.sales.maidav.model.purchase.Purchase;
import com.sales.maidav.web.dto.PurchaseForm;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface PurchaseService {
    Purchase create(PurchaseForm form, Long userId);
    Purchase findById(Long id);
    Page<Purchase> findPage(LocalDate from, LocalDate to, Long providerId, Long productId, int page);
}
