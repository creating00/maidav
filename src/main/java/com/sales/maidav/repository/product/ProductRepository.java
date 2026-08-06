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

    @EntityGraph(attributePaths = "provider")
    Optional<Product> findByBarcode(String barcode);

    @Query("select count(p) from Product p where p.stockAvailable <= p.stockMin")
    long countLowStock();

    @Query("select p from Product p join fetch p.provider where p.stockAvailable <= p.stockMin")
    List<Product> findLowStock();

    @EntityGraph(attributePaths = "provider")
    List<Product> findByProvider_Id(Long providerId);

    @Query(value = """
            select p.id from products p
            join providers provider on provider.id = p.provider_id
            where (:lowStock = false or p.stock_available <= p.stock_min)
              and ((:includeOutOfStock = true and p.stock_available <= 0)
                   or (:includeOutOfStock = false and p.stock_available > 0))
              and (:providerId is null or provider.id = :providerId)
              and (:term is null
                   or lower(unaccent(coalesce(p.product_code, ''))) like :term
                   or lower(unaccent(coalesce(p.barcode, ''))) like :term
                   or lower(unaccent(p.description)) like :term
                   or lower(unaccent(provider.name)) like :term)
              and (:applyUpdatedAfter = false or coalesce(p.updated_at, p.created_at) >= :updatedAfter)
              and (:applyUpdatedBefore = false or coalesce(p.updated_at, p.created_at) <= :updatedBefore)
            """,
            countQuery = """
            select count(*) from products p
            join providers provider on provider.id = p.provider_id
            where (:lowStock = false or p.stock_available <= p.stock_min)
              and ((:includeOutOfStock = true and p.stock_available <= 0)
                   or (:includeOutOfStock = false and p.stock_available > 0))
              and (:providerId is null or provider.id = :providerId)
              and (:term is null
                   or lower(unaccent(coalesce(p.product_code, ''))) like :term
                   or lower(unaccent(coalesce(p.barcode, ''))) like :term
                   or lower(unaccent(p.description)) like :term
                   or lower(unaccent(provider.name)) like :term)
              and (:applyUpdatedAfter = false or coalesce(p.updated_at, p.created_at) >= :updatedAfter)
              and (:applyUpdatedBefore = false or coalesce(p.updated_at, p.created_at) <= :updatedBefore)
            """, nativeQuery = true)
    Page<Long> findIdsPageForListing(@Param("lowStock") boolean lowStock,
                                     @Param("includeOutOfStock") boolean includeOutOfStock,
                                     @Param("providerId") Long providerId,
                                     @Param("term") String term,
                                     @Param("applyUpdatedAfter") boolean applyUpdatedAfter,
                                     @Param("updatedAfter") LocalDateTime updatedAfter,
                                     @Param("applyUpdatedBefore") boolean applyUpdatedBefore,
                                     @Param("updatedBefore") LocalDateTime updatedBefore,
                                     Pageable pageable);

    @EntityGraph(attributePaths = "provider")
    List<Product> findByIdIn(List<Long> ids);
}
