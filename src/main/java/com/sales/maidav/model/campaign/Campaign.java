package com.sales.maidav.model.campaign;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
public class Campaign extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(name = "item_count", nullable = false)
    private int itemCount;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_id", nullable = false)
    private User registeredBy;
    @Column(nullable = false)
    private boolean cancelled;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private User cancelledBy;
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<CampaignItem> items = new ArrayList<>();

    public CampaignFilter getState() {
        if (cancelled) return CampaignFilter.CANCELLED;
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) return CampaignFilter.FUTURE;
        if (today.isAfter(endDate)) return CampaignFilter.EXPIRED;
        return CampaignFilter.ACTIVE;
    }
}
