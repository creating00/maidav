package com.sales.maidav.model.product;

import jakarta.persistence.*;

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
}
