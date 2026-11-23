package com.sales.maidav.repository.campaign;

import com.sales.maidav.model.campaign.Campaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findDetail(Long id);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> lockForSale(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> lockForCancellation(Long id);
}
