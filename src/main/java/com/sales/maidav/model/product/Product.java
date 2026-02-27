package com.sales.maidav.model.product;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "price_wholesale_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceWholesaleNet;

    @Column(name = "price_retail_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceRetailNet;

    @Column(name = "vat_rate", nullable = false)
    private Integer vatRate;

    @Column(name = "price_wholesale", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceWholesale;

    @Column(name = "price_retail", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceRetail;

    @Column(name = "stock_available", nullable = false)
    private Integer stockAvailable;

    @Column(name = "stock_min", nullable = false)
    private Integer stockMin;

    @Column(name = "stock_max", nullable = false)
    private Integer stockMax;

    @Column(name = "image_path", length = 300)
    private String imagePath;

    public Product() {}

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public BigDecimal getPriceWholesaleNet() { return priceWholesaleNet; }
    public void setPriceWholesaleNet(BigDecimal priceWholesaleNet) { this.priceWholesaleNet = priceWholesaleNet; }

    public BigDecimal getPriceRetailNet() { return priceRetailNet; }
    public void setPriceRetailNet(BigDecimal priceRetailNet) { this.priceRetailNet = priceRetailNet; }

    public Integer getVatRate() { return vatRate; }
    public void setVatRate(Integer vatRate) { this.vatRate = vatRate; }

    public BigDecimal getPriceWholesale() { return priceWholesale; }
    public void setPriceWholesale(BigDecimal priceWholesale) { this.priceWholesale = priceWholesale; }

    public BigDecimal getPriceRetail() { return priceRetail; }
    public void setPriceRetail(BigDecimal priceRetail) { this.priceRetail = priceRetail; }

    public Integer getStockAvailable() { return stockAvailable; }
    public void setStockAvailable(Integer stockAvailable) { this.stockAvailable = stockAvailable; }

    public Integer getStockMin() { return stockMin; }
    public void setStockMin(Integer stockMin) { this.stockMin = stockMin; }

    public Integer getStockMax() { return stockMax; }
    public void setStockMax(Integer stockMax) { this.stockMax = stockMax; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
