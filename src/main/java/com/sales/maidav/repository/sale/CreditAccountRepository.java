package com.sales.maidav.repository.sale;

import com.sales.maidav.model.client.Zone;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    List<CreditAccount> findBySale_IdIn(List<Long> saleIds);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    List<CreditAccount> findBySale_Seller_Id(Long sellerId);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findByIdAndSale_Seller_Id(Long id, Long sellerId);

    @EntityGraph(attributePaths = {"client", "sale", "sale.seller"})
    Optional<CreditAccount> findBySale_IdAndSale_Seller_Id(Long saleId, Long sellerId);
    List<CreditAccount> findByClient_IdAndStatus(Long clientId, AccountStatus status);

    @Query("""
            select coalesce(sum(account.totalAmount), 0)
            from CreditAccount account
            where (:sellerId is null or account.sale.seller.id = :sellerId)
              and (:zoneId is null or account.client.zone.id = :zoneId)
              and account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
            """)
    java.math.BigDecimal sumTotalAmountByFilters(@Param("sellerId") Long sellerId,
                                                 @Param("zoneId") Long zoneId);

    @Query("""
            select coalesce(sum(account.balance), 0)
            from CreditAccount account
            where (:sellerId is null or account.sale.seller.id = :sellerId)
              and (:zoneId is null or account.client.zone.id = :zoneId)
              and account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
            """)
    java.math.BigDecimal sumBalanceByFilters(@Param("sellerId") Long sellerId,
                                             @Param("zoneId") Long zoneId);

    @Query("""
            select count(account)
            from CreditAccount account
            where (:sellerId is null or account.sale.seller.id = :sellerId)
              and (:zoneId is null or account.client.zone.id = :zoneId)
              and account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
            """)
    long countByFilters(@Param("sellerId") Long sellerId,
                        @Param("zoneId") Long zoneId);

    @Query("""
            select distinct seller
            from CreditAccount account
            join account.sale sale
            join sale.seller seller
            where account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
            order by seller.firstName, seller.lastName, seller.email
            """)
    List<User> findDistinctSellersWithCredits();

    @Query("""
            select distinct zone
            from CreditAccount account
            join account.client client
            join client.zone zone
            where account.status <> com.sales.maidav.model.sale.AccountStatus.VOID
            order by zone.address, zone.number
            """)
    List<Zone> findDistinctZonesWithCredits();

    @Query(value = "select coalesce(max(id), 0) + 1 from credit_accounts", nativeQuery = true)
    Long nextAccountId();
}
