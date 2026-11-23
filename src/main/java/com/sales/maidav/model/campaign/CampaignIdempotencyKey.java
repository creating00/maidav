package com.sales.maidav.model.campaign;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_idempotency_keys")
@Getter
@Setter
public class CampaignIdempotencyKey extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;
}
