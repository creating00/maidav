package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findBySale_Id(Long saleId);
}
