package com.sales.maidav.repository.purchase;

import com.sales.maidav.model.purchase.PurchaseIdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseIdempotencyKeyRepository extends JpaRepository<PurchaseIdempotencyKey, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into purchase_idempotency_keys (user_id, idempotency_key)
            values (:userId, :key) on conflict (user_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from PurchaseIdempotencyKey k where k.userId = :userId and k.idempotencyKey = :key")
    Optional<PurchaseIdempotencyKey> findForUpdate(@Param("userId") Long userId, @Param("key") String key);
}
