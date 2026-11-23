package com.sales.maidav.repository.campaign;

import com.sales.maidav.model.campaign.CampaignIdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CampaignIdempotencyKeyRepository extends JpaRepository<CampaignIdempotencyKey, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into campaign_idempotency_keys(user_id,idempotency_key) values (:userId,:key) on conflict(user_id,idempotency_key) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("key") String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from CampaignIdempotencyKey k where k.userId=:userId and k.idempotencyKey=:key")
    Optional<CampaignIdempotencyKey> findForUpdate(@Param("userId") Long userId, @Param("key") String key);
}
