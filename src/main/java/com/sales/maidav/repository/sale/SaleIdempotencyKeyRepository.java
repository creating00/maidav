package com.sales.maidav.repository.sale;

import com.sales.maidav.model.sale.SaleIdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SaleIdempotencyKeyRepository extends JpaRepository<SaleIdempotencyKey, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into sale_idempotency_keys (user_id, idempotency_key, created_at)
            values (:userId, :idempotencyKey, current_timestamp)
            on conflict (user_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select key
            from SaleIdempotencyKey key
            left join fetch key.sale
            where key.userId = :userId
              and key.idempotencyKey = :idempotencyKey
            """)
    Optional<SaleIdempotencyKey> findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
