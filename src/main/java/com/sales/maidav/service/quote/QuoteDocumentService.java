package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;

import java.util.List;

public interface QuoteDocumentService {

    byte[] generateQuotePdf(Quote quote);

    byte[] generatePreviewPdf(String quoteNumber, QuotePriceMode priceMode, List<QuoteItemInput> items);
}
