package com.sales.maidav.repository.product;

import com.sales.maidav.model.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByProvider_IdAndProductCode(Long providerId, String productCode);
    boolean existsByProvider_IdAndProductCodeAndIdNot(Long providerId, String productCode, Long id);

    @Query("select count(p) from Product p where p.stockAvailable <= p.stockMin")
    long countLowStock();

    @Query("select p from Product p where p.stockAvailable <= p.stockMin")
    List<Product> findLowStock();
}
