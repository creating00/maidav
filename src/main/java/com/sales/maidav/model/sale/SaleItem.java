package com.sales.maidav.model.sale;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
public class SaleItem extends BaseEntity {
    @Column(name = "campaign_item_id")
    private Long campaignItemId;
    @Column(name = "campaign_name", length = 150)
    private String campaignName;
    @Column(name = "campaign_normal_price", precision = 12, scale = 2)
    private BigDecimal campaignNormalPrice;

    public Long getCampaignItemId() { return campaignItemId; }
    public void setCampaignItemId(Long value) { campaignItemId = value; }
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String value) { campaignName = value; }
    public BigDecimal getCampaignNormalPrice() { return campaignNormalPrice; }
    public void setCampaignNormalPrice(BigDecimal value) { campaignNormalPrice = value; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public SaleItem() {}

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
