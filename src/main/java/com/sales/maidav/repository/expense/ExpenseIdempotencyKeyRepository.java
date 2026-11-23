package com.sales.maidav.repository.expense;

import com.sales.maidav.model.expense.ExpenseIdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExpenseIdempotencyKeyRepository extends JpaRepository<ExpenseIdempotencyKey, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into expense_idempotency_keys (user_id, idempotency_key)
            values (:userId, :key) on conflict (user_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ExpenseIdempotencyKey k where k.userId = :userId and k.idempotencyKey = :key")
    Optional<ExpenseIdempotencyKey> findForUpdate(@Param("userId") Long userId, @Param("key") String key);
}
