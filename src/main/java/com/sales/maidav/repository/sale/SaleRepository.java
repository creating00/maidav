package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    long countBySaleDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, SaleStatus status);
    long countByStatus(SaleStatus status);

    @EntityGraph(attributePaths = {"client", "seller"})
    List<Sale> findAll();

    @EntityGraph(attributePaths = {"client", "seller"})
    List<Sale> findAllByOrderBySaleDateDescIdDesc();

    @Override
    @EntityGraph(attributePaths = {"client", "seller"})
    java.util.Optional<Sale> findById(Long id);

    @Query(value = "select coalesce(max(id), 0) + 1 from sales", nativeQuery = true)
    Long nextSaleId();
}
