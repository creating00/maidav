package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.quote.InvalidQuoteException;
import com.sales.maidav.service.quote.QuoteCalculator;
import com.sales.maidav.service.quote.QuoteService;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteControllerTest {

    @Mock
    private QuoteService quoteService;
    @Mock
    private ProductService productService;
    @Mock
    private CompanySettingsService companySettingsService;

    private QuoteController quoteController;

    @BeforeEach
    void setUp() {
        quoteController = new QuoteController(
                quoteService,
                productService,
                companySettingsService,
                new QuoteCalculator()
        );
    }

    @Test
    void createRedirectsToSavedQuoteDetail() {
        Quote quote = new Quote();
        quote.setId(8L);

        when(quoteService.create(eq(QuotePriceMode.RETAIL), anyList())).thenReturn(quote);

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = quoteController.create(
                QuotePriceMode.RETAIL,
                java.util.List.of(1L),
                java.util.List.of(2),
                null,
                redirectAttributes,
                new ExtendedModelMap()
        );

        assertThat(view).isEqualTo("redirect:/quotes/8");
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                .isEqualTo("Presupuesto guardado correctamente");
    }

    @Test
    void createReturnsFormWhenServiceValidationFails() {
        when(quoteService.create(eq(QuotePriceMode.RETAIL), anyList()))
                .thenThrow(new InvalidQuoteException("Debe agregar al menos un producto"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = quoteController.create(
                QuotePriceMode.RETAIL,
                java.util.List.of(),
                java.util.List.of(),
                "{\"priceMode\":\"RETAIL\",\"items\":[]}",
                new RedirectAttributesModelMap(),
                model
        );

        assertThat(view).isEqualTo("pages/quotes/form");
        assertThat(model.get("formError")).isEqualTo("Debe agregar al menos un producto");
        assertThat(model.get("draftState")).isEqualTo("{\"priceMode\":\"RETAIL\",\"items\":[]}");
    }
}
