package com.sales.maidav.repository.product;

import com.sales.maidav.model.product.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Override
    @EntityGraph(attributePaths = "provider")
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "provider")
    Optional<Product> findById(Long id);

    boolean existsByProvider_IdAndProductCode(Long providerId, String productCode);
    boolean existsByProvider_IdAndProductCodeAndIdNot(Long providerId, String productCode, Long id);
    boolean existsByProductCode(String productCode);
    boolean existsByProductCodeAndIdNot(String productCode, Long id);
    boolean existsByBarcode(String barcode);
    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    @Query("select count(p) from Product p where p.stockAvailable <= p.stockMin")
    long countLowStock();

    @Query("select p from Product p join fetch p.provider where p.stockAvailable <= p.stockMin")
    List<Product> findLowStock();

    @EntityGraph(attributePaths = "provider")
    List<Product> findByProvider_Id(Long providerId);
}
