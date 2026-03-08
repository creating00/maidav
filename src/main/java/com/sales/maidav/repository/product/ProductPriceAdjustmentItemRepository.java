package com.sales.maidav.repository.product;

import com.sales.maidav.model.product.ProductPriceAdjustmentItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPriceAdjustmentItemRepository extends JpaRepository<ProductPriceAdjustmentItem, Long> {
    @EntityGraph(attributePaths = {"product", "adjustment"})
    List<ProductPriceAdjustmentItem> findByAdjustment_Id(Long adjustmentId);

    @EntityGraph(attributePaths = {"product", "adjustment"})
    List<ProductPriceAdjustmentItem> findByAdjustment_IdIn(List<Long> adjustmentIds);
}
