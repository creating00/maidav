package com.sales.maidav.model.quote;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_plan_options")
public class QuotePlanOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 30)
    private QuotePlanType planType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "promo_text", length = 160)
    private String promoText;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "cash_fee_amount", precision = 12, scale = 2)
    private BigDecimal cashFeeAmount;

    public Quote getQuote() {
        return quote;
    }

    public void setQuote(Quote quote) {
        this.quote = quote;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public QuotePlanType getPlanType() {
        return planType;
    }

    public void setPlanType(QuotePlanType planType) {
        this.planType = planType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPromoText() {
        return promoText;
    }

    public void setPromoText(String promoText) {
        this.promoText = promoText;
    }

    public Integer getInstallmentCount() {
        return installmentCount;
    }

    public void setInstallmentCount(Integer installmentCount) {
        this.installmentCount = installmentCount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getCashFeeAmount() {
        return cashFeeAmount;
    }

    public void setCashFeeAmount(BigDecimal cashFeeAmount) {
        this.cashFeeAmount = cashFeeAmount;
    }
}
