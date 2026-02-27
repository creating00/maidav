package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    long countBySaleDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, SaleStatus status);
    long countByStatus(SaleStatus status);

    @Query(value = "select coalesce(max(id), 0) + 1 from sales", nativeQuery = true)
    Long nextSaleId();
}
