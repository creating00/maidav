package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.QuotePriceMode;

import java.math.BigDecimal;
import java.util.List;

public record QuoteSalePreparation(Long quoteId, String quoteNumber, QuotePriceMode priceMode,
                                   List<Row> rows, List<PriceChange> changes, String fingerprint) {
    public record Row(Long productId, Integer quantity, BigDecimal unitPrice) {}

    public record PriceChange(String description, String code, BigDecimal previousPrice,
                              BigDecimal currentPrice) {}
}
