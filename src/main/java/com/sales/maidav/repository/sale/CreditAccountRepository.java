package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.AccountStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    List<CreditAccount> findAll();

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findById(Long id);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findBySale_Id(Long saleId);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    List<CreditAccount> findBySale_Seller_Id(Long sellerId);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findByIdAndSale_Seller_Id(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findBySale_IdAndSale_Seller_Id(Long saleId, Long sellerId);
    List<CreditAccount> findByClient_IdAndStatus(Long clientId, AccountStatus status);

    @Query(value = "select coalesce(max(id), 0) + 1 from credit_accounts", nativeQuery = true)
    Long nextAccountId();
}
