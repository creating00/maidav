package com.sales.maidav.repository.product;

import com.sales.maidav.model.product.ProductPriceAdjustment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPriceAdjustmentRepository extends JpaRepository<ProductPriceAdjustment, Long> {
    @EntityGraph(attributePaths = "provider")
    List<ProductPriceAdjustment> findTop20ByOrderByCreatedAtDesc();
}
