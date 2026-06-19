package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.SaleSellerChange;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleSellerChangeRepository extends JpaRepository<SaleSellerChange, Long> {

    @EntityGraph(attributePaths = {"previousSeller", "newSeller", "changedBy"})
    List<SaleSellerChange> findBySale_IdOrderByCreatedAtDescIdDesc(Long saleId);
}
