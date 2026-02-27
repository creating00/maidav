package com.sales.maidav.model.sale;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "credit_payments")
public class CreditPayment extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private CreditAccount account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    public CreditPayment() {}

    public CreditAccount getAccount() { return account; }
    public void setAccount(CreditAccount account) { this.account = account; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }
}
