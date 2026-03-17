package com.sales.maidav.service.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class DashboardPortfolioSnapshot {

    private final LocalDate cutoffDate;
    private final long creditsCount;
    private final BigDecimal totalFinanced;
    private final BigDecimal expectedCollected;
    private final BigDecimal collectedAmount;
    private final BigDecimal overdueDebt;
    private final BigDecimal pendingBalance;
    private final Long effectiveSellerId;
    private final Long effectiveZoneId;
    private final String effectiveSellerLabel;
    private final String effectiveZoneLabel;
    private final boolean adminView;

    public DashboardPortfolioSnapshot(LocalDate cutoffDate,
                                      long creditsCount,
                                      BigDecimal totalFinanced,
                                      BigDecimal expectedCollected,
                                      BigDecimal collectedAmount,
                                      BigDecimal overdueDebt,
                                      BigDecimal pendingBalance,
                                      Long effectiveSellerId,
                                      Long effectiveZoneId,
                                      String effectiveSellerLabel,
                                      String effectiveZoneLabel,
                                      boolean adminView) {
        this.cutoffDate = cutoffDate;
        this.creditsCount = creditsCount;
        this.totalFinanced = normalize(totalFinanced);
        this.expectedCollected = normalize(expectedCollected);
        this.collectedAmount = normalize(collectedAmount);
        this.overdueDebt = normalize(overdueDebt);
        this.pendingBalance = normalize(pendingBalance);
        this.effectiveSellerId = effectiveSellerId;
        this.effectiveZoneId = effectiveZoneId;
        this.effectiveSellerLabel = effectiveSellerLabel;
        this.effectiveZoneLabel = effectiveZoneLabel;
        this.adminView = adminView;
    }

    public LocalDate getCutoffDate() {
        return cutoffDate;
    }

    public long getCreditsCount() {
        return creditsCount;
    }

    public BigDecimal getTotalFinanced() {
        return totalFinanced;
    }

    public BigDecimal getExpectedCollected() {
        return expectedCollected;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public BigDecimal getOverdueDebt() {
        return overdueDebt;
    }

    public BigDecimal getPendingBalance() {
        return pendingBalance;
    }

    public Long getEffectiveSellerId() {
        return effectiveSellerId;
    }

    public Long getEffectiveZoneId() {
        return effectiveZoneId;
    }

    public String getEffectiveSellerLabel() {
        return effectiveSellerLabel;
    }

    public String getEffectiveZoneLabel() {
        return effectiveZoneLabel;
    }

    public boolean isAdminView() {
        return adminView;
    }

    public boolean isGlobalPortfolio() {
        return adminView && effectiveSellerId == null && effectiveZoneId == null;
    }

    public boolean isFiltered() {
        return !isGlobalPortfolio();
    }

    public BigDecimal getCollectionProgressPercent() {
        if (expectedCollected.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal progress = collectedAmount
                .multiply(new BigDecimal("100"))
                .divide(expectedCollected, 2, RoundingMode.HALF_UP);
        if (progress.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100.00");
        }
        if (progress.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return progress;
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
