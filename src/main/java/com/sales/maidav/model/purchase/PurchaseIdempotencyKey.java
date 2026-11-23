package com.sales.maidav.model.purchase;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "purchase_idempotency_keys")
@Getter
@Setter
public class PurchaseIdempotencyKey extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;
}
