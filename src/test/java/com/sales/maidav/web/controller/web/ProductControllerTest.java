package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.product.ProviderService;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProviderService providerService;
    @Mock
    private CompanySettingsService companySettingsService;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productController = new ProductController(productService, providerService, companySettingsService);
    }

    @Test
    void lookupBarcodeReturnsProductPayload() {
        Product product = new Product();
        product.setId(25L);
        product.setBarcode("7791234567890");
        product.setDescription("Producto Demo");

        when(productService.findByBarcode("7791234567890")).thenReturn(product);

        Map<String, Object> payload = productController.lookupBarcode("7791234567890");

        assertThat(payload)
                .containsEntry("productId", 25L)
                .containsEntry("barcode", "7791234567890")
                .containsEntry("description", "Producto Demo");
    }

    @Test
    void listHidesOutOfStockByDefault() {
        when(companySettingsService.getSettings()).thenReturn(new CompanySettings());
        when(productService.findPageForListing(eq(false), eq(false), eq(null), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(providerService.findAll()).thenReturn(List.of());
        when(productService.findRecentAdjustments()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        String view = productController.list(null, null, null, null, null, 0, null, model);

        assertThat(view).isEqualTo("pages/products/index");
        assertThat(model.get("includeOutOfStock")).isEqualTo(false);
        verify(productService).findPageForListing(false, false, null, null, null, PageRequest.of(0, 15, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("id"))));
    }

    @Test
    void listAllowsIncludingOutOfStockWhenRequested() {
        when(companySettingsService.getSettings()).thenReturn(new CompanySettings());
        when(productService.findPageForListing(eq(false), eq(true), eq("mesa"), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(providerService.findAll()).thenReturn(List.of());
        when(productService.findRecentAdjustments()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        productController.list(null, true, "mesa", null, null, 0, null, model);

        assertThat(model.get("includeOutOfStock")).isEqualTo(true);
        verify(productService).findPageForListing(eq(false), eq(true), eq("mesa"), eq(null), eq(null), any(PageRequest.class));
    }
}
