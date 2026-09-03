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
    void generatesPdfAndExcelPriceLists() throws Exception {
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
        product.setStockAvailable(18);

        CompanySettings settings = new CompanySettings();
        settings.setCalcMultContado(new BigDecimal("1.70"));
        byte[] pdf = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.FINANCING,
                ExportDocumentService.ExportFormat.PDF, settings);
        byte[] excel = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.WHOLESALE,
                ExportDocumentService.ExportFormat.EXCEL, settings);

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(excel).startsWith(new byte[]{'P', 'K'});

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue()).isEqualTo("Stock");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("Precio mayorista efectivo");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue()).isEqualTo("Precio mayorista transferencia");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(3).getStringCellValue()).isEqualTo("18");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(4).getStringCellValue()).isEqualTo("$ 200.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(5).getStringCellValue()).isEqualTo("$ 150.00");
        }
    }

    @Test
    void includesRetailCashAndTransferPricesInExcelExport() throws Exception {
        Product product = new Product();
        product.setProductCode("P-003");
        product.setDescription("Mesa auxiliar");
        product.setCost(new BigDecimal("200.00"));
        product.setVatRate(new BigDecimal("21.00"));
        product.setPriceRetail(new BigDecimal("410.00"));

        CompanySettings settings = new CompanySettings();
        settings.setCalcMultContado(new BigDecimal("1.30"));

        byte[] excel = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.RETAIL,
                ExportDocumentService.ExportFormat.EXCEL, settings);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("Precio minorista efectivo");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue()).isEqualTo("Precio minorista transferencia");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(4).getStringCellValue()).isEqualTo("$ 300.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(5).getStringCellValue()).isEqualTo("$ 410.00");
        }
    }

    @Test
    void includesFinancingCashAndTransferInstallmentsInExcelExport() throws Exception {
        Product product = new Product();
        product.setProductCode("P-004");
        product.setDescription("Placard");
        product.setCost(new BigDecimal("1000.00"));
        product.setVatRate(BigDecimal.ZERO);

        CompanySettings settings = new CompanySettings();
        settings.setCalcMultContado(BigDecimal.ONE);
        settings.setCalcMultDebito(new BigDecimal("1.50"));
        settings.setCalcRecargo(new BigDecimal("2.00"));
        settings.setCalcIntDia(new BigDecimal("2.00"));
        settings.setCalcDias(100);
        settings.setCalcIntSem(new BigDecimal("2.00"));
        settings.setCalcSemanas(10);
        settings.setCalcIntMesCorto(new BigDecimal("2.00"));
        settings.setCalcMesesCorto(4);
        settings.setCalcIntMesLargo(new BigDecimal("2.00"));
        settings.setCalcMesesLargo(8);

        byte[] excel = exportDocumentService.productPrices(List.of(product), ExportDocumentService.PriceListType.FINANCING,
                ExportDocumentService.ExportFormat.EXCEL, settings);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("Efectivo");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue()).isEqualTo("Transferencia");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue()).isEqualTo("8 cuotas contado");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue()).isEqualTo("8 cuotas transferencia");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue()).isEqualTo("4 cuotas contado");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue()).isEqualTo("4 cuotas transferencia");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(10).getStringCellValue()).isEqualTo("10 semanas contado");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(11).getStringCellValue()).isEqualTo("10 semanas transferencia");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(12).getStringCellValue()).isEqualTo("100 días transferencia");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(4).getStringCellValue()).isEqualTo("$ 1000.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(5).getStringCellValue()).isEqualTo("$ 1500.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(6).getStringCellValue()).isEqualTo("$ 250.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(7).getStringCellValue()).isEqualTo("$ 500.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(8).getStringCellValue()).isEqualTo("$ 500.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(9).getStringCellValue()).isEqualTo("$ 1000.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(10).getStringCellValue()).isEqualTo("$ 200.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(11).getStringCellValue()).isEqualTo("$ 400.00");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(12).getStringCellValue()).isEqualTo("$ 50.00");
        }
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
