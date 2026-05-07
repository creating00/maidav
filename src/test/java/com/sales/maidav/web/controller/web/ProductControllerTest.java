package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.product.ProviderService;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
