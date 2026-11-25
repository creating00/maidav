package com.sales.maidav.service.quote;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuoteItem;
import com.sales.maidav.repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuoteSaleServiceImpl implements QuoteSaleService {
    private final QuoteService quoteService;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public QuoteSalePreparation prepare(Long quoteId) {
        Quote quote = quoteService.findById(quoteId);
        if (quote.getItems().isEmpty()) {
            throw new InvalidQuoteException("El presupuesto no tiene productos");
        }
        List<QuoteSalePreparation.Row> rows = new ArrayList<>();
        List<QuoteSalePreparation.PriceChange> changes = new ArrayList<>();
        StringBuilder fingerprint = new StringBuilder(quoteId + ":" + quote.getPriceMode());
        for (QuoteItem item : quote.getItems()) {
            String label = item.getProductDescription() + " (" + item.getProductCode() + ")";
            if (item.getProduct() == null) {
                throw new InvalidQuoteException("El artículo " + label + " ya no está disponible");
            }
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new InvalidQuoteException("El artículo " + label + " ya no está disponible"));
            BigDecimal price;
            try {
                price = QuotePricingSupport.resolveVisibleUnitPrice(product, quote.getPriceMode());
            } catch (InvalidQuoteException ex) {
                throw new InvalidQuoteException("El artículo " + label + " no tiene un precio vigente válido");
            }
            rows.add(new QuoteSalePreparation.Row(product.getId(), item.getQuantity(), price));
            if (price.compareTo(item.getUnitPrice()) != 0) {
                changes.add(new QuoteSalePreparation.PriceChange(item.getProductDescription(),
                        item.getProductCode(), item.getUnitPrice(), price));
            }
            fingerprint.append('|').append(product.getId()).append(':').append(item.getQuantity())
                    .append(':').append(price.toPlainString());
        }
        return new QuoteSalePreparation(quote.getId(), quote.getQuoteNumber(), quote.getPriceMode(),
                List.copyOf(rows), List.copyOf(changes), digest(fingerprint.toString()));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }
}
