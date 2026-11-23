package com.sales.maidav.service.finance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class FinanceExclusions {
    private final BigDecimal registeredSales;
    private final long voidCashCount;
    private final BigDecimal voidCashAmount;
    private final long excludedCreditCount;
    private final BigDecimal excludedCreditAmount;

    public boolean isPresent() {
        return voidCashCount > 0 || excludedCreditCount > 0;
    }
}
