package com.sales.maidav.service.campaign;

import com.sales.maidav.model.campaign.CampaignItem;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class CampaignOffer {
    private final Long itemId;
    private final Long campaignId;
    private final String campaignName;
    private final Long productId;
    private final String productCode;
    private final String productDescription;
    private final String imagePath;
    private final BigDecimal normalPrice;
    private final BigDecimal offerPrice;
    private final LocalDate endDate;
    private final int stock;

    public CampaignOffer(CampaignItem item) {
        itemId = item.getId();
        campaignId = item.getCampaign().getId();
        campaignName = item.getCampaign().getName();
        productId = item.getProduct().getId();
        productCode = item.getProductCode();
        productDescription = item.getProductDescription();
        imagePath = item.getImagePath();
        normalPrice = item.getNormalPrice();
        offerPrice = item.getOfferPrice();
        endDate = item.getCampaign().getEndDate();
        stock = item.getProduct().getStockAvailable();
    }
}
