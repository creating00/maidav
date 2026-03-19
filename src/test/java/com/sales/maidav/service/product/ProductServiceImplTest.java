package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.Provider;
import com.sales.maidav.repository.product.ProductPriceAdjustmentItemRepository;
import com.sales.maidav.repository.product.ProductPriceAdjustmentRepository;
import com.sales.maidav.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPriceAdjustmentRepository productPriceAdjustmentRepository;
    @Mock
    private ProductPriceAdjustmentItemRepository productPriceAdjustmentItemRepository;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productRepository,
                productPriceAdjustmentRepository,
                productPriceAdjustmentItemRepository
        );
    }

    @Test
    void createGeneratesUniqueProductCodeWhenMissing() {
        when(productRepository.existsByProductCode(anyString())).thenReturn(false);
        when(productRepository.existsByProvider_IdAndProductCode(anyLong(), anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product product = validProduct();
        product.setProductCode(null);

        Product saved = productService.create(product);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        assertThat(saved.getProductCode()).isNotBlank().startsWith("PRD-");
        assertThat(captor.getValue().getProductCode()).isEqualTo(saved.getProductCode());
        assertThat(saved.getPriceWholesale()).isEqualByComparingTo("1210.00");
        assertThat(saved.getPriceRetail()).isEqualByComparingTo("1452.00");
    }

    private Product validProduct() {
        Provider provider = new Provider();
        provider.setId(10L);
        provider.setName("Proveedor Demo");

        Product product = new Product();
        product.setProvider(provider);
        product.setDescription("Producto demo");
        product.setBarcode("12345678");
        product.setCost(new BigDecimal("1000.00"));
        product.setPriceWholesaleNet(new BigDecimal("1000.00"));
        product.setPriceRetailNet(new BigDecimal("1200.00"));
        product.setVatRate(new BigDecimal("21.00"));
        product.setStockAvailable(5);
        product.setStockMin(1);
        product.setStockMax(10);
        return product;
    }
}
