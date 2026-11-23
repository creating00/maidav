package com.sales.maidav.model.campaign;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "campaign_items")
@Getter
@Setter
public class CampaignItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;
    @Column(name = "product_description", nullable = false, length = 200)
    private String productDescription;
    @Column(name = "image_path", length = 300)
    private String imagePath;
    @Column(name = "normal_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal normalPrice;
    @Column(name = "offer_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal offerPrice;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_mode", nullable = false, length = 20)
    private DiscountMode discountMode;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
}
