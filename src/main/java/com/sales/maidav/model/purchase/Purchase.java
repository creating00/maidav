package com.sales.maidav.model.purchase;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.product.Provider;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases")
@Getter
@Setter
public class Purchase extends BaseEntity {
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(name = "provider_name", length = 150)
    private String providerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_id", nullable = false)
    private User registeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentCollectionMethod paymentMethod;

    @Column(length = 2000)
    private String notes;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<PurchaseItem> items = new ArrayList<>();
}
