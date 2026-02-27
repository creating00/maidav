package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {
    Optional<CreditAccount> findBySale_Id(Long saleId);
    List<CreditAccount> findByClient_IdAndStatus(Long clientId, AccountStatus status);

    @Query(value = "select coalesce(max(id), 0) + 1 from credit_accounts", nativeQuery = true)
    Long nextAccountId();
}
