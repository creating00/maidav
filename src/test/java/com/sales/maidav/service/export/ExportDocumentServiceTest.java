package com.sales.maidav.service.export;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.Provider;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @Test
    void includesAssociatedProductsInSalesExcelExport() throws Exception {
        Product product = new Product();
        product.setProductCode("P-002");
        product.setDescription("Silla plegable");
        Sale sale = new Sale();
        sale.setId(12L);
        sale.setSaleNumber("V-0012");
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("24000"));

        byte[] excel = exportDocumentService.sales(List.of(sale), Map.of(12L, List.of(item)), ExportDocumentService.ExportFormat.EXCEL);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            String products = workbook.getSheetAt(0).getRow(2).getCell(7).getStringCellValue();
            assertThat(products).contains("2 x P-002 - Silla plegable").contains("24000.00");
        }
    }
}
