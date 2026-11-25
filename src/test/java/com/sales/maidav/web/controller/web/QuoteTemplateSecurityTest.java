package com.sales.maidav.web.controller.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.quote.*;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(QuoteTemplateSecurityTest.Config.class)
class QuoteTemplateSecurityTest {
    @Autowired QuoteController quotes;
    @Autowired QuoteSaleController transfer;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sellerCannotPinThroughTheController() {
        authenticate("ROLE_VENDEDOR", "QUOTE_READ");
        assertThatThrownBy(() -> quotes.pin(8L, true, new RedirectAttributesModelMap()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void transferRequiresBothQuoteReadAndSalesCreateForGetAndPost() {
        for (String permission : new String[]{"QUOTE_READ", "SALES_CREATE"}) {
            authenticate(permission);
            assertThatThrownBy(() -> transfer.preview(8L, new ExtendedModelMap(), new RedirectAttributesModelMap()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> transfer.accept(8L, "hash", new ExtendedModelMap(), new RedirectAttributesModelMap()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user@example.com", "n/a", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean QuoteController quotes() {
            return new QuoteController(mock(QuoteService.class), mock(ProductService.class),
                    mock(CompanySettingsService.class), new QuoteCalculator(), mock(QuoteDocumentService.class));
        }
        @Bean QuoteSaleController transfer() {
            return new QuoteSaleController(mock(QuoteSaleService.class), new ObjectMapper());
        }
    }
}
