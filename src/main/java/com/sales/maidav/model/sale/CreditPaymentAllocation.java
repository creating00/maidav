package com.sales.maidav.model.sale;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_payment_allocations", uniqueConstraints =
        @UniqueConstraint(name = "ux_credit_payment_allocations_payment_installment",
                columnNames = {"payment_id", "installment_id"}))
public class CreditPaymentAllocation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private CreditAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private CreditPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", nullable = false)
    private CreditInstallment installment;

    @Column(name = "collected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    @Column(name = "impact_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal impactAmount;

    @Column(nullable = false)
    private boolean verified;

    public CreditAccount getAccount() { return account; }
    public void setAccount(CreditAccount account) { this.account = account; }
    public CreditPayment getPayment() { return payment; }
    public void setPayment(CreditPayment payment) { this.payment = payment; }
    public CreditInstallment getInstallment() { return installment; }
    public void setInstallment(CreditInstallment installment) { this.installment = installment; }
    public BigDecimal getCollectedAmount() { return collectedAmount; }
    public void setCollectedAmount(BigDecimal collectedAmount) { this.collectedAmount = collectedAmount; }
    public BigDecimal getImpactAmount() { return impactAmount; }
    public void setImpactAmount(BigDecimal impactAmount) { this.impactAmount = impactAmount; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
