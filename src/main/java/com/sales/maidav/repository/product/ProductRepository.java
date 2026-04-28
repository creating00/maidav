package com.sales.maidav.repository.product;

import com.sales.maidav.model.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @EntityGraph(attributePaths = "provider")
    @Query(value = """
            select p from Product p
            join p.provider provider
            where (:lowStock = false or p.stockAvailable <= p.stockMin)
              and (:providerId is null or provider.id = :providerId)
              and (:term is null
                   or lower(coalesce(p.productCode, '')) like :term
                   or lower(coalesce(p.barcode, '')) like :term
                   or lower(p.description) like :term
                   or lower(provider.name) like :term)
              and (:applyUpdatedAfter = false or coalesce(p.updatedAt, p.createdAt) >= :updatedAfter)
              and (:applyUpdatedBefore = false or coalesce(p.updatedAt, p.createdAt) <= :updatedBefore)
            """,
            countQuery = """
            select count(p) from Product p
            join p.provider provider
            where (:lowStock = false or p.stockAvailable <= p.stockMin)
              and (:providerId is null or provider.id = :providerId)
              and (:term is null
                   or lower(coalesce(p.productCode, '')) like :term
                   or lower(coalesce(p.barcode, '')) like :term
                   or lower(p.description) like :term
                   or lower(provider.name) like :term)
              and (:applyUpdatedAfter = false or coalesce(p.updatedAt, p.createdAt) >= :updatedAfter)
              and (:applyUpdatedBefore = false or coalesce(p.updatedAt, p.createdAt) <= :updatedBefore)
            """)
    Page<Product> findPageForListing(@Param("lowStock") boolean lowStock,
                                     @Param("providerId") Long providerId,
                                     @Param("term") String term,
                                     @Param("applyUpdatedAfter") boolean applyUpdatedAfter,
                                     @Param("updatedAfter") LocalDateTime updatedAfter,
                                     @Param("applyUpdatedBefore") boolean applyUpdatedBefore,
                                     @Param("updatedBefore") LocalDateTime updatedBefore,
                                     Pageable pageable);
}
