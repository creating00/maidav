package com.sales.maidav.service.quote;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuoteItem;
import com.sales.maidav.model.quote.QuotePlanOption;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class QuoteDocumentServiceImpl implements QuoteDocumentService {

    private static final Logger log = LoggerFactory.getLogger(QuoteDocumentServiceImpl.class);
    private static final int PDF_IMAGE_CANVAS_SIZE = 320;

    private static final Color INK = new Color(20, 44, 68);
    private static final Color BRAND = new Color(15, 94, 118);
    private static final Color PAPER = new Color(248, 244, 237);
    private static final Color SOFT = new Color(228, 221, 209);
    private static final Color ACCENT = new Color(179, 118, 64);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 21, Font.BOLD, INK);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, BRAND);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(92, 88, 84));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, INK);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
    private static final Font MUTED_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(92, 88, 84));
    private static final Font PLAN_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, INK);
    private static final Font PROMO_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, ACCENT);

    private final ProductRepository productRepository;
    private final CompanySettingsService companySettingsService;
    private final QuoteCalculator quoteCalculator;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public byte[] generateQuotePdf(Quote quote) {
        CompanySettings settings = companySettingsService.getSettings();
        List<QuotePdfItem> items = quote.getItems().stream()
                .map(item -> new QuotePdfItem(
                        item.getProductDescription(),
                        item.getProductCode(),
                        item.getProductImagePath(),
                        item.getQuantity(),
                        scaled(item.getUnitPrice()),
                        scaled(item.getLineTotal())
                ))
                .toList();
        List<QuotePdfPlan> plans = quote.getPlanOptions().stream()
                .map(option -> new QuotePdfPlan(option.getTitle(), option.getPromoText()))
                .toList();
        QuotePdfModel model = new QuotePdfModel(
                quote.getQuoteNumber(),
                quote.getPriceMode(),
                quote.getCreatedAt(),
                settings,
                items,
                plans,
                scaled(quote.getTotalAmount()),
                scaled(quote.getCashAmount()),
                scaled(quote.getDebitAmount())
        );
        return renderPdf(model);
    }

    @Override
    public byte[] generatePreviewPdf(String quoteNumber, QuotePriceMode priceMode, List<QuoteItemInput> inputs) {
        if (priceMode == null) {
            throw new InvalidQuoteException("Debe seleccionar el tipo de precio");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidQuoteException("Debe agregar al menos un producto");
        }

        CompanySettings settings = companySettingsService.getSettings();
        List<QuotePdfItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal financingBaseAmount = BigDecimal.ZERO;

        for (QuoteItemInput input : inputs) {
            if (input.productId() == null || input.quantity() == null || input.quantity() <= 0) {
                throw new InvalidQuoteException("Las cantidades del carrito deben ser validas");
            }
            Product product = productRepository.findById(input.productId())
                    .orElseThrow(() -> new InvalidQuoteException("Producto no encontrado"));
            BigDecimal unitPrice = QuotePricingSupport.resolveVisibleUnitPrice(product, priceMode);
            BigDecimal financingUnitPrice = QuotePricingSupport.resolveFinancingUnitPrice(product, priceMode);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(input.quantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal financingLineTotal = financingUnitPrice
                    .multiply(BigDecimal.valueOf(input.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            items.add(new QuotePdfItem(
                    product.getDescription(),
                    product.getProductCode(),
                    product.getImagePath(),
                    input.quantity(),
                    unitPrice,
                    lineTotal
            ));
            totalAmount = totalAmount.add(lineTotal);
            financingBaseAmount = financingBaseAmount.add(financingLineTotal);
        }

        BigDecimal normalizedTotal = scaled(totalAmount);
        BigDecimal normalizedFinancingBaseAmount = scaled(financingBaseAmount);
        List<QuotePdfPlan> plans = quoteCalculator.calculatePlanSnapshots(normalizedFinancingBaseAmount, settings).stream()
                .map(plan -> new QuotePdfPlan(plan.title(), plan.promoText()))
                .toList();

        QuotePdfModel model = new QuotePdfModel(
                quoteNumber,
                priceMode,
                LocalDateTime.now(),
                settings,
                items,
                plans,
                normalizedTotal,
                quoteCalculator.calculateCashTotal(normalizedFinancingBaseAmount, settings),
                quoteCalculator.calculateDebitTotal(normalizedFinancingBaseAmount, settings)
        );
        return renderPdf(model);
    }

    private byte[] renderPdf(QuotePdfModel model) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 34, 34, 30, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            addHeader(document, model);
            addOverview(document, model);
            addItems(document, model.items());
            addPlans(document, model.plans());
            addFooter(document, model.companySettings());

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new InvalidQuoteException("No se pudo generar el PDF del presupuesto");
        }
    }

    private void addHeader(Document document, QuotePdfModel model) throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(new float[]{1.1f, 1.7f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell brandCell = new PdfPCell();
        brandCell.setBorder(Rectangle.NO_BORDER);
        brandCell.setBackgroundColor(PAPER);
        brandCell.setPadding(18);
        brandCell.setMinimumHeight(110);
        Image logo = loadImage(model.companySettings().getLogoPath(), 72, 72);
        if (logo != null) {
            logo.setAlignment(Element.ALIGN_LEFT);
            brandCell.addElement(logo);
            brandCell.addElement(Chunk.NEWLINE);
        }
        Paragraph brand = new Paragraph(defaultText(model.companySettings().getName(), "Maidav"), TITLE_FONT);
        brand.setSpacingAfter(4);
        brandCell.addElement(brand);
        brandCell.addElement(new Paragraph("Presupuesto comercial", SUBTITLE_FONT));
        header.addCell(brandCell);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setBackgroundColor(INK);
        metaCell.setPadding(18);
        metaCell.setMinimumHeight(110);
        metaCell.addElement(metaLine("Numero", defaultText(model.quoteNumber(), "Borrador")));
        metaCell.addElement(metaLine("Fecha", DATE_FORMAT.format(model.createdAt())));
        metaCell.addElement(metaLine("Lista", model.priceMode() == QuotePriceMode.WHOLESALE ? "Mayorista" : "Minorista"));
        metaCell.addElement(metaLine("Estado", model.quoteNumber() == null || model.quoteNumber().isBlank() ? "Previsualizacion" : "Emitido"));
        header.addCell(metaCell);

        document.add(header);
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(10);
        document.add(spacer);
    }

    private Paragraph metaLine(String label, String value) {
        Font whiteLabel = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(189, 214, 230));
        Font whiteValue = new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE);
        Paragraph paragraph = new Paragraph();
        paragraph.setSpacingAfter(6);
        paragraph.add(new Chunk(label.toUpperCase(Locale.ROOT) + "\n", whiteLabel));
        paragraph.add(new Chunk(value, whiteValue));
        return paragraph;
    }

    private void addOverview(Document document, QuotePdfModel model) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 1f, 1f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.addCell(summaryCell("Productos", String.valueOf(model.items().size()), PAPER));
        table.addCell(summaryCell("Total productos", money(model.totalAmount()), new Color(234, 244, 247)));
        table.addCell(summaryCell("Contado", money(model.cashAmount()), new Color(231, 245, 237)));
        table.addCell(summaryCell("Debito / transferencia", money(model.debitAmount()), new Color(235, 238, 250)));
        document.add(table);
    }

    private PdfPCell summaryCell(String label, String value, Color background) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(SOFT);
        cell.setBorderWidth(0.8f);
        cell.setBackgroundColor(background);
        cell.setPadding(12);
        Paragraph labelParagraph = new Paragraph(label.toUpperCase(Locale.ROOT), LABEL_FONT);
        labelParagraph.setSpacingAfter(4);
        cell.addElement(labelParagraph);
        cell.addElement(new Paragraph(value, VALUE_FONT));
        return cell;
    }

    private void addItems(Document document, List<QuotePdfItem> items) throws DocumentException, IOException {
        Paragraph title = new Paragraph("Detalle de productos", VALUE_FONT);
        title.setSpacingBefore(4);
        title.setSpacingAfter(8);
        document.add(title);

        for (QuotePdfItem item : items) {
            PdfPTable table = new PdfPTable(new float[]{0.75f, 1.45f, 0.8f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(10);

            PdfPCell imageCell = new PdfPCell();
            imageCell.setBackgroundColor(PAPER);
            imageCell.setBorderColor(SOFT);
            imageCell.setPadding(8);
            imageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            imageCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            imageCell.setFixedHeight(108);
            Image image = loadImage(item.imagePath(), 86, 86);
            if (image != null) {
                image.setAlignment(Element.ALIGN_CENTER);
                imageCell.addElement(image);
            } else {
                Paragraph placeholder = new Paragraph("Sin imagen", MUTED_FONT);
                placeholder.setAlignment(Element.ALIGN_CENTER);
                imageCell.addElement(placeholder);
            }
            table.addCell(imageCell);

            PdfPCell detailCell = new PdfPCell();
            detailCell.setBorderColor(SOFT);
            detailCell.setPadding(12);
            detailCell.addElement(new Paragraph(defaultText(item.description(), "Producto"), VALUE_FONT));
            detailCell.addElement(new Paragraph("Codigo: " + defaultText(item.code(), "-"), MUTED_FONT));
            detailCell.addElement(new Paragraph(item.quantity() + " unidad/es", BODY_FONT));
            detailCell.addElement(new Paragraph("Precio unitario " + money(item.unitPrice()), BODY_FONT));
            table.addCell(detailCell);

            PdfPCell amountCell = new PdfPCell();
            amountCell.setBorderColor(SOFT);
            amountCell.setPadding(12);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph amountLabel = new Paragraph("Subtotal", LABEL_FONT);
            amountLabel.setAlignment(Element.ALIGN_RIGHT);
            amountCell.addElement(amountLabel);
            Paragraph amount = new Paragraph(money(item.lineTotal()), VALUE_FONT);
            amount.setAlignment(Element.ALIGN_RIGHT);
            amountCell.addElement(amount);
            table.addCell(amountCell);

            document.add(table);
        }
    }

    private void addPlans(Document document, List<QuotePdfPlan> plans) throws DocumentException {
        Paragraph title = new Paragraph("Opciones de financiacion", VALUE_FONT);
        title.setSpacingBefore(4);
        title.setSpacingAfter(8);
        document.add(title);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        for (QuotePdfPlan plan : plans) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(SOFT);
            cell.setPadding(12);
            cell.setBackgroundColor(Color.WHITE);
            cell.addElement(new Paragraph(plan.title(), PLAN_FONT));
            if (plan.promoText() != null && !plan.promoText().isBlank()) {
                Paragraph promo = new Paragraph(plan.promoText(), PROMO_FONT);
                promo.setSpacingBefore(4);
                cell.addElement(promo);
            }
            table.addCell(cell);
        }
        document.add(table);
    }

    private void addFooter(Document document, CompanySettings settings) throws DocumentException {
        Paragraph footer = new Paragraph();
        footer.setSpacingBefore(14);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.add(new Chunk(defaultText(settings.getAddress(), ""), MUTED_FONT));
        if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
            footer.add(new Chunk("  |  " + settings.getPhone(), MUTED_FONT));
        }
        if (settings.getEmail() != null && !settings.getEmail().isBlank()) {
            footer.add(new Chunk("  |  " + settings.getEmail(), MUTED_FONT));
        }
        document.add(footer);
    }

    private Image loadImage(String publicPath, float maxWidth, float maxHeight) throws BadElementException, IOException {
        Path file = resolvePublicAsset(publicPath);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            Image image = Image.getInstance(prepareImageBytes(file));
            image.scaleToFit(maxWidth, maxHeight);
            return image;
        } catch (BadElementException | IOException ex) {
            log.warn("Skipping unsupported quote image {}: {}", publicPath, ex.getMessage());
            return null;
        }
    }

    private byte[] prepareImageBytes(Path file) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            return Files.readAllBytes(file);
        }

        BufferedImage canvas = new BufferedImage(PDF_IMAGE_CANVAS_SIZE, PDF_IMAGE_CANVAS_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(PAPER);
            graphics.fillRect(0, 0, PDF_IMAGE_CANVAS_SIZE, PDF_IMAGE_CANVAS_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.min(
                    (double) PDF_IMAGE_CANVAS_SIZE / source.getWidth(),
                    (double) PDF_IMAGE_CANVAS_SIZE / source.getHeight()
            );
            int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (PDF_IMAGE_CANVAS_SIZE - drawWidth) / 2;
            int y = (PDF_IMAGE_CANVAS_SIZE - drawHeight) / 2;
            graphics.drawImage(source, x, y, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(canvas, "png", output)) {
            throw new IOException("No PNG writer available for PDF image conversion");
        }
        return output.toByteArray();
    }

    private Path resolvePublicAsset(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return null;
        }
        String normalized = publicPath.replace('\\', '/').trim();
        if (!normalized.startsWith("/uploads/")) {
            return null;
        }
        String relative = normalized.substring("/uploads/".length());
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path candidate = root.resolve(relative).normalize();
        return candidate.startsWith(root) ? candidate : null;
    }

    private String money(BigDecimal amount) {
        return "$" + scaled(amount).stripTrailingZeros().toPlainString();
    }

    private BigDecimal scaled(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record QuotePdfModel(
            String quoteNumber,
            QuotePriceMode priceMode,
            LocalDateTime createdAt,
            CompanySettings companySettings,
            List<QuotePdfItem> items,
            List<QuotePdfPlan> plans,
            BigDecimal totalAmount,
            BigDecimal cashAmount,
            BigDecimal debitAmount
    ) {
    }

    private record QuotePdfItem(
            String description,
            String code,
            String imagePath,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }

    private record QuotePdfPlan(String title, String promoText) {
    }
}
