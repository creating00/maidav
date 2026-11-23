package com.sales.maidav.repository.campaign;

import com.sales.maidav.model.campaign.CampaignItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;

public interface CampaignItemRepository extends JpaRepository<CampaignItem, Long> {
    @Override
    @EntityGraph(attributePaths = "product")
    Optional<CampaignItem> findById(Long id);
    @EntityGraph(attributePaths = {"product", "campaign"})
    @Query("select i from CampaignItem i join i.product p where i.campaign.cancelled = false and i.campaign.startDate <= :today and i.campaign.endDate >= :today order by i.campaign.endDate, i.id")
    List<CampaignItem> findActive(LocalDate today, Pageable pageable);
}
