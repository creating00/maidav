package com.sales.maidav.service.campaign;

import com.sales.maidav.model.campaign.*;
import com.sales.maidav.web.dto.CampaignForm;
import org.springframework.data.domain.Page;
import java.util.List;

public interface CampaignService {
    Campaign create(CampaignForm form, Long userId);
    Campaign findById(Long id);
    Page<Campaign> findPage(CampaignFilter state, int page);
    CampaignOffer findActiveOffer(Long itemId);
    CampaignOffer findOffer(Long itemId);
    CampaignOffer lockActiveOffer(Long itemId);
    List<CampaignOffer> findActiveHighlights();
    void cancel(Long id, Long userId);
}
