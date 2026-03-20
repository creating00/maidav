package com.sales.maidav.model.product;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_price_adjustments")
public class ProductPriceAdjustment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private PriceAdjustmentType adjustmentType;

    @Column(name = "percentage", precision = 8, scale = 4)
    private BigDecimal percentage;

    @Column(name = "factor_applied", precision = 16, scale = 8)
    private BigDecimal factorApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private PriceAdjustmentScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(name = "products_affected", nullable = false)
    private Integer productsAffected;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "undone", nullable = false)
    private boolean undone;

    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    @Column(name = "undone_by", length = 120)
    private String undoneBy;

    public PriceAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(PriceAdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public BigDecimal getFactorApplied() {
        return factorApplied;
    }

    public void setFactorApplied(BigDecimal factorApplied) {
        this.factorApplied = factorApplied;
    }

    public Provider getProvider() {
        return provider;
    }

    public PriceAdjustmentScope getScope() {
        return scope;
    }

    public void setScope(PriceAdjustmentScope scope) {
        this.scope = scope;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Integer getProductsAffected() {
        return productsAffected;
    }

    public void setProductsAffected(Integer productsAffected) {
        this.productsAffected = productsAffected;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isUndone() {
        return undone;
    }

    public void setUndone(boolean undone) {
        this.undone = undone;
    }

    public LocalDateTime getUndoneAt() {
        return undoneAt;
    }

    public void setUndoneAt(LocalDateTime undoneAt) {
        this.undoneAt = undoneAt;
    }

    public String getUndoneBy() {
        return undoneBy;
    }

    public void setUndoneBy(String undoneBy) {
        this.undoneBy = undoneBy;
    }
}
