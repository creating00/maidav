package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.SaleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @EntityGraph(attributePaths = {"product", "product.provider"})
    List<SaleItem> findBySale_IdOrderByIdAsc(Long saleId);
}
