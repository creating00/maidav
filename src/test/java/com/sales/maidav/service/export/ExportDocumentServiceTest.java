package com.sales.maidav.service.export;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.Provider;
import com.sales.maidav.model.settings.CompanySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportDocumentServiceTest {

    private final ExportDocumentService exportDocumentService = new ExportDocumentService();

    @Test
    void generatesPdfAndExcelPriceLists() {
        Product product = new Product();
        Provider provider = new Provider();
        provider.setName("Proveedor demo");
        product.setProvider(provider);
        product.setProductCode("P-001");
        product.setDescription("Producto demo");
        product.setCost(new BigDecimal("100.00"));
        product.setVatRate(new BigDecimal("21.00"));
        product.setPriceWholesale(new BigDecimal("150.00"));
        product.setPriceRetail(new BigDecimal("200.00"));

        CompanySettings settings = new CompanySettings();
        byte[] pdf = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.FINANCING,
                ExportDocumentService.ExportFormat.PDF, settings);
        byte[] excel = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.WHOLESALE,
                ExportDocumentService.ExportFormat.EXCEL, settings);

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(excel).startsWith(new byte[]{'P', 'K'});
    }
}
