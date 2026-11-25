package com.sales.maidav.service.quote;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuoteItem;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSaleServiceImplTest {
    @Mock QuoteService quoteService;
    @Mock ProductRepository productRepository;
    QuoteSaleService service;
    Quote quote;
    Product product;
    QuoteItem item;

    @BeforeEach
    void setUp() {
        service = new QuoteSaleServiceImpl(quoteService, productRepository);
        quote = new Quote();
        quote.setId(8L);
        quote.setQuoteNumber("P-000008");
        quote.setPriceMode(QuotePriceMode.RETAIL);
        product = new Product();
        product.setId(7L);
        product.setPriceRetail(new BigDecimal("20000.00"));
        product.setPriceWholesale(new BigDecimal("18000.00"));
        item = new QuoteItem();
        item.setProduct(product);
        item.setProductCode("PDR-6P45AB");
        item.setProductDescription("Licuadora Dheli");
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("20000.00"));
        quote.addItem(item);
        when(quoteService.findById(8L)).thenReturn(quote);
    }

    @Test
    void unchangedPricesCopyQuantitiesWithoutChangingOriginalQuote() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        QuoteSalePreparation preparation = service.prepare(8L);
        assertThat(preparation.changes()).isEmpty();
        assertThat(preparation.rows()).containsExactly(new QuoteSalePreparation.Row(7L, 3, new BigDecimal("20000.00")));
        assertThat(item.getUnitPrice()).isEqualByComparingTo("20000.00");
    }

    @Test
    void priceIncreaseAndDecreaseAreDetectedWithArticleAndCode() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        product.setPriceRetail(new BigDecimal("25000.00"));
        QuoteSalePreparation increased = service.prepare(8L);
        assertThat(increased.changes()).containsExactly(new QuoteSalePreparation.PriceChange(
                "Licuadora Dheli", "PDR-6P45AB", new BigDecimal("20000.00"), new BigDecimal("25000.00")));
        assertThat(increased.rows().getFirst().unitPrice()).isEqualByComparingTo("25000.00");
        product.setPriceRetail(new BigDecimal("19000.00"));
        QuoteSalePreparation decreased = service.prepare(8L);
        assertThat(decreased.changes()).hasSize(1);
        assertThat(decreased.fingerprint()).isNotEqualTo(increased.fingerprint());
        assertThat(item.getUnitPrice()).isEqualByComparingTo("20000.00");
    }

    @Test
    void wholesaleComparisonDoesNotUseRetailPrice() {
        quote.setPriceMode(QuotePriceMode.WHOLESALE);
        item.setUnitPrice(new BigDecimal("18000"));
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        QuoteSalePreparation preparation = service.prepare(8L);
        assertThat(preparation.changes()).isEmpty();
        assertThat(preparation.rows().getFirst().unitPrice()).isEqualByComparingTo("18000");
    }

    @Test
    void deletedProductBlocksTransferInsteadOfDroppingTheItem() {
        item.setProduct(null);
        assertThatThrownBy(() -> service.prepare(8L)).isInstanceOf(InvalidQuoteException.class)
                .hasMessageContaining("PDR-6P45AB").hasMessageContaining("ya no está disponible");
    }

    @Test
    void invalidCurrentPriceBlocksTransfer() {
        product.setPriceRetail(BigDecimal.ZERO);
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.prepare(8L)).isInstanceOf(InvalidQuoteException.class)
                .hasMessageContaining("PDR-6P45AB").hasMessageContaining("precio vigente válido");
    }

    @Test
    void repeatedUseAlwaysReadsCurrentPricesAndKeepsEveryItem() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        QuoteItem second = new QuoteItem();
        second.setProduct(product);
        second.setQuantity(2);
        second.setUnitPrice(new BigDecimal("20000.00"));
        quote.addItem(second);
        assertThat(service.prepare(8L).rows()).hasSize(2);
        assertThat(service.prepare(8L).rows()).extracting(QuoteSalePreparation.Row::quantity).containsExactly(3, 2);
        assertThat(quote.getItems()).hasSize(2);
    }
}
