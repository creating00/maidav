package com.sales.maidav.model.sale;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "credit_accounts")
public class CreditAccount extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "weeks_count", nullable = false)
    private Integer weeksCount;

    @Column(name = "due_day")
    private Integer dueDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", length = 20)
    private PaymentFrequency paymentFrequency;

    @Column(name = "due_days", length = 50)
    private String dueDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.OPEN;

    public CreditAccount() {}

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Integer getWeeksCount() { return weeksCount; }
    public void setWeeksCount(Integer weeksCount) { this.weeksCount = weeksCount; }

    public Integer getDueDay() { return dueDay; }
    public void setDueDay(Integer dueDay) { this.dueDay = dueDay; }

    public PaymentFrequency getPaymentFrequency() { return paymentFrequency; }
    public void setPaymentFrequency(PaymentFrequency paymentFrequency) { this.paymentFrequency = paymentFrequency; }

    public String getDueDays() { return dueDays; }
    public void setDueDays(String dueDays) { this.dueDays = dueDays; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
}
