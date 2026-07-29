package com.sales.maidav.service.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExportDocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] productPrices(List<Product> products, PriceListType type, ExportFormat format, CompanySettings settings) {
        List<String> headers = productHeaders(type, settings);
        List<List<String>> rows = products.stream().map(product -> productRow(product, type, settings)).toList();
        String title = "Lista de precios " + type.label();
        return generate(title, headers, rows, format);
    }

    public byte[] sales(List<Sale> sales, Map<Long, List<SaleItem>> saleItems, ExportFormat format) {
        List<String> headers = List.of("Número", "Fecha", "Cliente", "DNI", "Vendedor", "Forma de pago", "Estado", "Productos", "Descuento", "Total");
        List<List<String>> rows = sales.stream().map(sale -> List.of(
                value(sale.getSaleNumber(), String.valueOf(sale.getId())),
                sale.getSaleDate() == null ? "" : DATE_FORMAT.format(sale.getSaleDate()),
                clientName(sale),
                sale.getClient() == null ? "" : value(sale.getClient().getNationalId(), ""),
                sale.getSeller() == null ? "" : value(sale.getSeller().getEmail(), ""),
                sale.getPaymentType() == null ? "" : (sale.getPaymentType().name().equals("CREDIT") ? "Crédito" : "Contado"),
                sale.getStatus() == null ? "" : (sale.getStatus().name().equals("VOID") ? "Anulada" : "Activa"),
                products(saleItems.getOrDefault(sale.getId(), List.of())),
                money(sale.getDiscountAmount()),
                money(sale.getTotalAmount())
        )).toList();
        return generate("Ventas", headers, rows, format);
    }

    private byte[] generate(String title, List<String> headers, List<List<String>> rows, ExportFormat format) {
        return format == ExportFormat.PDF ? pdf(title, headers, rows) : excel(title, headers, rows);
    }

    private byte[] pdf(String title, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 30, 30);
            PdfWriter.getInstance(document, output);
            document.open();
            Paragraph heading = new Paragraph(title, new Font(Font.HELVETICA, 15, Font.BOLD));
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            document.add(new Paragraph("Generado el " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, new Font(Font.HELVETICA, 8, Font.BOLD)));
                cell.setBackgroundColor(new java.awt.Color(226, 232, 240));
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (List<String> row : rows) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Paragraph(value, new Font(Font.HELVETICA, 7)));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el PDF", ex);
        }
    }

    private byte[] excel(String title, List<String> headers, List<List<String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(title.length() > 31 ? title.substring(0, 31) : title);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            int rowIndex = 0;
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue(title);
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIndex++);
            for (int column = 0; column < headers.size(); column++) {
                Cell cell = headerRow.createCell(column);
                cell.setCellValue(headers.get(column));
                cell.setCellStyle(headerStyle);
            }
            for (List<String> values : rows) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            sheet.createFreezePane(0, 2);
            for (int column = 0; column < headers.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 16000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el Excel", ex);
        }
    }

    private List<String> productHeaders(PriceListType type, CompanySettings settings) {
        List<String> headers = new ArrayList<>(List.of("Código", "Descripción", "Proveedor", "Stock"));
        if (type == PriceListType.WHOLESALE) headers.add("Precio mayorista");
        if (type == PriceListType.RETAIL) headers.add("Precio minorista");
        if (type == PriceListType.FINANCING) {
            headers.add("Efectivo");
            headers.add("Débito");
            headers.add("Diaria (" + whole(settings.getCalcDias(), 144) + ")");
            headers.add("Semanal (" + whole(settings.getCalcSemanas(), 13) + ")");
            headers.add("Mensual (" + whole(settings.getCalcMesesCorto(), 4) + ")");
            headers.add("Mensual (" + whole(settings.getCalcMesesLargo(), 8) + ")");
        }
        return headers;
    }

    private List<String> productRow(Product product, PriceListType type, CompanySettings settings) {
        List<String> row = new ArrayList<>(List.of(
                value(product.getProductCode(), ""),
                value(product.getDescription(), ""),
                product.getProvider() == null ? "" : value(product.getProvider().getName(), ""),
                String.valueOf(product.getStockAvailable() == null ? 0 : product.getStockAvailable())
        ));
        if (type == PriceListType.WHOLESALE) row.add(money(product.getPriceWholesale()));
        if (type == PriceListType.RETAIL) row.add(money(product.getPriceRetail()));
        if (type == PriceListType.FINANCING) {
            BigDecimal base = value(product.getCost()).multiply(BigDecimal.ONE.add(value(product.getVatRate()).movePointLeft(2)));
            BigDecimal recargo = positive(settings.getCalcRecargo(), "1.26");
            row.add(money(round50(base.multiply(positive(settings.getCalcMultContado(), "1.30")))));
            row.add(money(round50(base.multiply(positive(settings.getCalcMultDebito(), "1.50")))));
            row.add(money(installment(base, settings.getCalcIntDia(), recargo, whole(settings.getCalcDias(), 144))));
            row.add(money(installment(base, settings.getCalcIntSem(), recargo, whole(settings.getCalcSemanas(), 13))));
            row.add(money(installment(base, settings.getCalcIntMesCorto(), recargo, whole(settings.getCalcMesesCorto(), 4))));
            row.add(money(installment(base, settings.getCalcIntMesLargo(), recargo, whole(settings.getCalcMesesLargo(), 8))));
        }
        return row;
    }

    private BigDecimal installment(BigDecimal base, BigDecimal interest, BigDecimal recargo, int count) {
        return round50(base.multiply(positive(interest, "2")).multiply(recargo).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal round50(BigDecimal value) { return value.divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(50)); }
    private BigDecimal positive(BigDecimal value, String fallback) { return value != null && value.signum() > 0 ? value : new BigDecimal(fallback); }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private int whole(Integer value, int fallback) { return value == null || value < 1 ? fallback : value; }
    private String money(BigDecimal value) { return "$ " + value(value).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String clientName(Sale sale) { return sale.getClient() == null ? "" : (value(sale.getClient().getLastName(), "") + ", " + value(sale.getClient().getFirstName(), "")).replaceAll("^, |, $", ""); }
    private String products(List<SaleItem> items) {
        if (items.isEmpty()) return "Sin productos";
        return items.stream().map(item -> {
            String product = item.getProduct() == null ? "Producto sin detalle"
                    : value(item.getProduct().getProductCode(), "") + " - " + value(item.getProduct().getDescription(), "");
            return item.getQuantity() + " x " + product + " (" + money(item.getLineTotal()) + ")";
        }).collect(java.util.stream.Collectors.joining(" | "));
    }

    public enum ExportFormat { PDF, EXCEL }
    public enum PriceListType {
        WHOLESALE("mayorista"), RETAIL("minorista"), FINANCING("financiados");
        private final String label;
        PriceListType(String label) { this.label = label; }
        public String label() { return label; }
    }
}
