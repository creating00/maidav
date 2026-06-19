package com.sales.maidav.model.sale;

import com.sales.maidav.model.common.BaseEntity;
import com.sales.maidav.model.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_seller_changes")
public class SaleSellerChange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_seller_id", nullable = false)
    private User previousSeller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_seller_id", nullable = false)
    private User newSeller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public User getPreviousSeller() {
        return previousSeller;
    }

    public void setPreviousSeller(User previousSeller) {
        this.previousSeller = previousSeller;
    }

    public User getNewSeller() {
        return newSeller;
    }

    public void setNewSeller(User newSeller) {
        this.newSeller = newSeller;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }
}
