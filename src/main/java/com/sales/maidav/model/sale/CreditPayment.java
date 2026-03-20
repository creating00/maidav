package com.sales.maidav.model.sale;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "credit_payments")
public class CreditPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private CreditAccount account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "impact_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal impactAmount;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    @Column(name = "registered_by", length = 150)
    private String registeredBy;

    @Column(name = "allocation_summary", length = 255)
    private String allocationSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentCollectionMethod paymentMethod;

    @Column(name = "operation_token", length = 120)
    private String operationToken;

    @Column(nullable = false)
    private boolean reversal = false;

    @Column(name = "reversal_of_payment_id")
    private Long reversalOfPaymentId;

    @Column(name = "target_installment_id")
    private Long targetInstallmentId;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    public CreditPayment() {}

    public CreditAccount getAccount() { return account; }
    public void setAccount(CreditAccount account) { this.account = account; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getImpactAmount() { return impactAmount; }
    public void setImpactAmount(BigDecimal impactAmount) { this.impactAmount = impactAmount; }

    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }

    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }

    public String getAllocationSummary() { return allocationSummary; }
    public void setAllocationSummary(String allocationSummary) { this.allocationSummary = allocationSummary; }

    public PaymentCollectionMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentCollectionMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getOperationToken() { return operationToken; }
    public void setOperationToken(String operationToken) { this.operationToken = operationToken; }

    public boolean isReversal() { return reversal; }
    public void setReversal(boolean reversal) { this.reversal = reversal; }

    public Long getReversalOfPaymentId() { return reversalOfPaymentId; }
    public void setReversalOfPaymentId(Long reversalOfPaymentId) { this.reversalOfPaymentId = reversalOfPaymentId; }

    public Long getTargetInstallmentId() { return targetInstallmentId; }
    public void setTargetInstallmentId(Long targetInstallmentId) { this.targetInstallmentId = targetInstallmentId; }

    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}

