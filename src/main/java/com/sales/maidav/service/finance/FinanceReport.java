package com.sales.maidav.service.finance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class FinanceReport {
    private final LocalDate from;
    private final LocalDate to;
    private final List<FinanceAggregate> aggregates;
    private final FinanceExclusions exclusions;
    private final Page<FinanceMovement> movements;

    public BigDecimal getCashSales() { return total("CASH_SALE"); }
    public BigDecimal getCreditCollections() { return total("CREDIT_PAYMENT"); }
    public BigDecimal getCreditAdjustments() { return total("CREDIT_REVERSAL"); }
    public BigDecimal getPurchases() { return total("PURCHASE").negate(); }
    public BigDecimal getExpenses() { return total("EXPENSE").negate(); }
    public BigDecimal getIncome() { return getCashSales().add(getCreditCollections()).add(getCreditAdjustments()); }
    public BigDecimal getPayments() { return getPurchases().add(getExpenses()); }
    public BigDecimal getDifference() { return getIncome().subtract(getPayments()); }

    public List<FinanceMethodSummary> getMethods() {
        return aggregates.stream().collect(Collectors.groupingBy(FinanceAggregate::getPaymentMethod, TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(entry -> {
                    BigDecimal income = BigDecimal.ZERO;
                    BigDecimal payments = BigDecimal.ZERO;
                    for (FinanceAggregate aggregate : entry.getValue()) {
                        if (aggregate.getKind().equals("PURCHASE") || aggregate.getKind().equals("EXPENSE")) payments = payments.subtract(aggregate.getAmount());
                        else income = income.add(aggregate.getAmount());
                    }
                    return new FinanceMethodSummary(entry.getKey(), income, payments);
                }).toList();
    }

    private BigDecimal total(String kind) {
        return aggregates.stream().filter(row -> row.getKind().equals(kind)).map(FinanceAggregate::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
