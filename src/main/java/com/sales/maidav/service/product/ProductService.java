package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.ProductPriceAdjustment;
import com.sales.maidav.model.product.PriceAdjustmentScope;
import com.sales.maidav.model.product.PriceAdjustmentType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductService {
    List<Product> findAll();
    Product findById(Long id);
    Product create(Product product);
    Product update(Long id, Product product);
    void delete(Long id);
    long count();
    long countLowStock();
    List<Product> findLowStock();
    long bulkAdjustPrices(BigDecimal percentage, Long providerId, PriceAdjustmentType adjustmentType, PriceAdjustmentScope scope);
    List<ProductPriceAdjustment> findRecentAdjustments();
    Map<Long, String> findAdjustmentProductCodes(List<Long> adjustmentIds);
    void undoAdjustment(Long adjustmentId);
    String generateSystemBarcode();
    String renderBarcodeLabelSvg(String barcode);
}
