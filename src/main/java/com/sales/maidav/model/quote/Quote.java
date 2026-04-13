package com.sales.maidav.model.quote;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotes")
public class Quote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "quote_number", length = 20)
    private String quoteNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    private QuotePriceMode priceMode;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    @Column(name = "product_summary", nullable = false, length = 255)
    private String productSummary;

    @Column(name = "pricing_base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricingBaseAmount = BigDecimal.ZERO;

    @Column(name = "financing_base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal financingBaseAmount = BigDecimal.ZERO;

    @Column(name = "cash_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashAmount = BigDecimal.ZERO;

    @Column(name = "debit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<QuoteItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<QuotePlanOption> planOptions = new ArrayList<>();

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public String getQuoteNumber() {
        return quoteNumber;
    }

    public void setQuoteNumber(String quoteNumber) {
        this.quoteNumber = quoteNumber;
    }

    public QuotePriceMode getPriceMode() {
        return priceMode;
    }

    public void setPriceMode(QuotePriceMode priceMode) {
        this.priceMode = priceMode;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public String getProductSummary() {
        return productSummary;
    }

    public void setProductSummary(String productSummary) {
        this.productSummary = productSummary;
    }

    public BigDecimal getPricingBaseAmount() {
        return pricingBaseAmount;
    }

    public void setPricingBaseAmount(BigDecimal pricingBaseAmount) {
        this.pricingBaseAmount = pricingBaseAmount;
    }

    public BigDecimal getFinancingBaseAmount() {
        return financingBaseAmount;
    }

    public void setFinancingBaseAmount(BigDecimal financingBaseAmount) {
        this.financingBaseAmount = financingBaseAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(BigDecimal cashAmount) {
        this.cashAmount = cashAmount;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount;
    }

    public List<QuoteItem> getItems() {
        return items;
    }

    public void setItems(List<QuoteItem> items) {
        this.items = items;
    }

    public List<QuotePlanOption> getPlanOptions() {
        return planOptions;
    }

    public void setPlanOptions(List<QuotePlanOption> planOptions) {
        this.planOptions = planOptions;
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.setQuote(this);
    }

    public void addPlanOption(QuotePlanOption option) {
        planOptions.add(option);
        option.setQuote(this);
    }

    public QuotePlanOption getDailyPlan() {
        return planByType(QuotePlanType.DAILY);
    }

    public QuotePlanOption getWeeklyPlan() {
        return planByType(QuotePlanType.WEEKLY);
    }

    public QuotePlanOption getMonthlyShortPlan() {
        return planByType(QuotePlanType.MONTHLY_SHORT);
    }

    public QuotePlanOption getMonthlyLongPlan() {
        return planByType(QuotePlanType.MONTHLY_LONG);
    }

    private QuotePlanOption planByType(QuotePlanType type) {
        for (QuotePlanOption option : planOptions) {
            if (option.getPlanType() == type) {
                return option;
            }
        }
        return null;
    }
}
