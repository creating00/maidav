package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.QuotePlanType;

import java.math.BigDecimal;

public record QuotePlanSnapshot(
        QuotePlanType planType,
        String title,
        String promoText,
        Integer installmentCount,
        BigDecimal feeAmount,
        BigDecimal cashFeeAmount,
        Integer displayOrder
) {
}
