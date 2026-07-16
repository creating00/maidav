package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;

import java.math.BigDecimal;

public class MorositySummary {
    private final Client client;
    private final String sellerDisplay;
    private final long daysOverdue;
    private final BigDecimal amountDue;
    private final MorosityLevel level;

    public MorositySummary(Client client, String sellerDisplay, long daysOverdue, BigDecimal amountDue, MorosityLevel level) {
        this.client = client;
        this.sellerDisplay = sellerDisplay;
        this.daysOverdue = daysOverdue;
        this.amountDue = amountDue;
        this.level = level;
    }

    public Client getClient() { return client; }
    public String getSellerDisplay() { return sellerDisplay; }
    public long getDaysOverdue() { return daysOverdue; }
    public BigDecimal getAmountDue() { return amountDue; }
    public MorosityLevel getLevel() { return level; }
}
