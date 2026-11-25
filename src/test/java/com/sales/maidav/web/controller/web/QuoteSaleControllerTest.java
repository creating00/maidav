package com.sales.maidav.web.controller.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.service.quote.InvalidQuoteException;
import com.sales.maidav.service.quote.QuoteSalePreparation;
import com.sales.maidav.service.quote.QuoteSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSaleControllerTest {
    @Mock QuoteSaleService service;
    QuoteSaleController controller;
    ExtendedModelMap model;
    RedirectAttributesModelMap redirect;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new QuoteSaleController(service, mapper);
        model = new ExtendedModelMap();
        redirect = new RedirectAttributesModelMap();
    }

    @Test
    void unchangedQuoteLoadsSaleDraftWithAllItemsAndPriceMode() throws Exception {
        when(service.prepare(8L)).thenReturn(preparation(false, "current"));
        assertThat(controller.preview(8L, model, redirect)).isEqualTo("redirect:/sales/new");
        var draft = mapper.readTree((String) redirect.getFlashAttributes().get("quoteDraftState"));
        assertThat(draft.get("priceMode").asText()).isEqualTo("WHOLESALE");
        assertThat(draft.get("rows").size()).isEqualTo(2);
        assertThat(draft.get("rows").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(draft.get("rows").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("25000");
    }

    @Test
    void changedQuoteRequiresConfirmationBeforePreparingSaleDraft() {
        var preparation = preparation(true, "current");
        when(service.prepare(8L)).thenReturn(preparation);
        assertThat(controller.preview(8L, model, redirect)).isEqualTo("pages/quotes/use");
        assertThat(model.get("preparation")).isSameAs(preparation);
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }

    @Test
    void acceptingCurrentPricesContinuesToSales() {
        when(service.prepare(8L)).thenReturn(preparation(true, "current"));
        assertThat(controller.accept(8L, "current", model, redirect)).isEqualTo("redirect:/sales/new");
    }

    @Test
    void concurrentPriceChangeRequiresAnotherConfirmation() {
        when(service.prepare(8L)).thenReturn(preparation(true, "new-prices"));
        assertThat(controller.accept(8L, "old-prices", model, redirect)).isEqualTo("pages/quotes/use");
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }

    @Test
    void missingProductReturnsAnErrorWithoutStartingAPartialSale() {
        when(service.prepare(8L)).thenThrow(new InvalidQuoteException("Artículo no disponible"));
        assertThat(controller.preview(8L, model, redirect)).isEqualTo("redirect:/quotes");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Artículo no disponible");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("quoteDraftState");
    }

    private QuoteSalePreparation preparation(boolean changed, String fingerprint) {
        return new QuoteSalePreparation(8L, "P-000008", QuotePriceMode.WHOLESALE,
                List.of(new QuoteSalePreparation.Row(7L, 3, new BigDecimal("25000.00")),
                        new QuoteSalePreparation.Row(9L, 1, new BigDecimal("8000.00"))),
                changed ? List.of(new QuoteSalePreparation.PriceChange("Licuadora", "PDR-6P45AB",
                        new BigDecimal("20000.00"), new BigDecimal("25000.00"))) : List.of(), fingerprint);
    }
}
