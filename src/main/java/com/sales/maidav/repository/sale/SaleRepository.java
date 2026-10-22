package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = {"client", "seller"})
    List<Sale> findBySeller_IdOrderBySaleDateDescIdDesc(Long sellerId);

    @Query(value = """
            select sale
            from Sale sale
            join fetch sale.client client
            join fetch sale.seller seller
            left join CreditAccount account on account.sale = sale and account.status <> :voidAccountStatus
            where (:sellerId is null or seller.id = :sellerId)
              and (:includeVoided = true or sale.status <> :voidSaleStatus)
              and (:paymentType is null or sale.paymentType = :paymentType)
              and (:term is null
                   or lower(function('unaccent', coalesce(sale.saleNumber, ''))) like :term
                   or lower(function('unaccent', coalesce(client.nationalId, ''))) like :term
                   or lower(function('unaccent', coalesce(client.firstName, ''))) like :term
                   or lower(function('unaccent', coalesce(client.lastName, ''))) like :term
                   or lower(function('unaccent', coalesce(seller.email, ''))) like :term
                   or lower(function('unaccent', coalesce(account.accountNumber, ''))) like :term
                   or lower(cast(sale.paymentType as string)) like :term
                   or lower(cast(sale.status as string)) like :term)
            order by sale.saleDate desc, sale.id desc
            """,
            countQuery = """
                    select count(sale)
                    from Sale sale
                    join sale.client client
                    join sale.seller seller
                    left join CreditAccount account on account.sale = sale and account.status <> :voidAccountStatus
                    where (:sellerId is null or seller.id = :sellerId)
                      and (:includeVoided = true or sale.status <> :voidSaleStatus)
                      and (:paymentType is null or sale.paymentType = :paymentType)
                      and (:term is null
                           or lower(function('unaccent', coalesce(sale.saleNumber, ''))) like :term
                           or lower(function('unaccent', coalesce(client.nationalId, ''))) like :term
                           or lower(function('unaccent', coalesce(client.firstName, ''))) like :term
                           or lower(function('unaccent', coalesce(client.lastName, ''))) like :term
                           or lower(function('unaccent', coalesce(seller.email, ''))) like :term
                           or lower(function('unaccent', coalesce(account.accountNumber, ''))) like :term
                           or lower(cast(sale.paymentType as string)) like :term
                           or lower(cast(sale.status as string)) like :term)
                    """)
    Page<Sale> findPageForListing(Long sellerId,
                                  PaymentType paymentType,
                                  boolean includeVoided,
                                  SaleStatus voidSaleStatus,
                                  AccountStatus voidAccountStatus,
                                  String term,
                                  Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"client", "seller"})
    java.util.Optional<Sale> findById(Long id);

    @EntityGraph(attributePaths = {"client", "seller"})
    java.util.Optional<Sale> findByIdAndSeller_Id(Long id, Long sellerId);

    @Query(value = "select coalesce(max(id), 0) + 1 from sales", nativeQuery = true)
    Long nextSaleId();

    long countBySeller_Id(Long sellerId);
}
