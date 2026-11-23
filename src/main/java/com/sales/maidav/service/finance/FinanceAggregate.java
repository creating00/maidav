package com.sales.maidav.service.finance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class FinanceAggregate {
    private final String kind;
    private final String paymentMethod;
    private final BigDecimal amount;
    private final long count;
}
