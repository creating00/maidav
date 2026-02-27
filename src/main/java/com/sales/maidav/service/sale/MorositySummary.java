package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;

import java.math.BigDecimal;

public class MorositySummary {
    private final Client client;
    private final long daysOverdue;
    private final BigDecimal amountDue;
    private final MorosityLevel level;

    public MorositySummary(Client client, long daysOverdue, BigDecimal amountDue, MorosityLevel level) {
        this.client = client;
        this.daysOverdue = daysOverdue;
        this.amountDue = amountDue;
        this.level = level;
    }

    public Client getClient() { return client; }
    public long getDaysOverdue() { return daysOverdue; }
    public BigDecimal getAmountDue() { return amountDue; }
    public MorosityLevel getLevel() { return level; }
}
