package com.sales.maidav.service.sale;

import java.math.BigDecimal;

public class SaleItemInput {
    private final Long productId;
    private final Integer quantity;
    private final BigDecimal unitPrice;
    private final Long campaignItemId;

    public SaleItemInput(Long productId, Integer quantity, BigDecimal unitPrice) {
        this(productId, quantity, unitPrice, null);
    }

    public SaleItemInput(Long productId, Integer quantity, BigDecimal unitPrice, Long campaignItemId) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.campaignItemId = campaignItemId;
    }

    public Long getCampaignItemId() { return campaignItemId; }

    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
