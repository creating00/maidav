package com.sales.maidav.repository.purchase;

import com.sales.maidav.model.purchase.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {
    @Override
    @EntityGraph(attributePaths = "registeredBy")
    Page<Purchase> findAll(Specification<Purchase> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "registeredBy"})
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findDetailById(Long id);
}
