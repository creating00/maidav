package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;

import java.util.List;

public interface QuoteService {
    List<Quote> findAll();
    Quote findById(Long id);
    Quote create(QuotePriceMode priceMode, List<QuoteItemInput> items);
    void delete(Long id);
    String previewNextQuoteNumber();
}
