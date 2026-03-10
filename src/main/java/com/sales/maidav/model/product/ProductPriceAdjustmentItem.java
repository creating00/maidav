package com.sales.maidav.model.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_price_adjustment_items")
public class ProductPriceAdjustmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private ProductPriceAdjustment adjustment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "previous_cost", precision = 12, scale = 2)
    private BigDecimal previousCost;

    @Column(name = "new_cost", precision = 12, scale = 2)
    private BigDecimal newCost;

    @Column(name = "previous_price_wholesale_net", precision = 12, scale = 2)
    private BigDecimal previousPriceWholesaleNet;

    @Column(name = "new_price_wholesale_net", precision = 12, scale = 2)
    private BigDecimal newPriceWholesaleNet;

    @Column(name = "previous_price_retail_net", precision = 12, scale = 2)
    private BigDecimal previousPriceRetailNet;

    @Column(name = "new_price_retail_net", precision = 12, scale = 2)
    private BigDecimal newPriceRetailNet;

    public Long getId() {
        return id;
    }

    public ProductPriceAdjustment getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(ProductPriceAdjustment adjustment) {
        this.adjustment = adjustment;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getPreviousCost() {
        return previousCost;
    }

    public void setPreviousCost(BigDecimal previousCost) {
        this.previousCost = previousCost;
    }

    public BigDecimal getNewCost() {
        return newCost;
    }

    public void setNewCost(BigDecimal newCost) {
        this.newCost = newCost;
    }

    public BigDecimal getPreviousPriceWholesaleNet() {
        return previousPriceWholesaleNet;
    }

    public void setPreviousPriceWholesaleNet(BigDecimal previousPriceWholesaleNet) {
        this.previousPriceWholesaleNet = previousPriceWholesaleNet;
    }

    public BigDecimal getNewPriceWholesaleNet() {
        return newPriceWholesaleNet;
    }

    public void setNewPriceWholesaleNet(BigDecimal newPriceWholesaleNet) {
        this.newPriceWholesaleNet = newPriceWholesaleNet;
    }

    public BigDecimal getPreviousPriceRetailNet() {
        return previousPriceRetailNet;
    }

    public void setPreviousPriceRetailNet(BigDecimal previousPriceRetailNet) {
        this.previousPriceRetailNet = previousPriceRetailNet;
    }

    public BigDecimal getNewPriceRetailNet() {
        return newPriceRetailNet;
    }

    public void setNewPriceRetailNet(BigDecimal newPriceRetailNet) {
        this.newPriceRetailNet = newPriceRetailNet;
    }
}
