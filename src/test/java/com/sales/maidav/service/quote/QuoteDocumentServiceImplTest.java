package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuoteItem;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteDocumentServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CompanySettingsService companySettingsService;
    @Mock
    private QuoteCalculator quoteCalculator;

    @TempDir
    Path tempDir;

    @Test
    void generateQuotePdfSkipsUnsupportedProductImage() throws Exception {
        Path productDir = Files.createDirectories(tempDir.resolve("products"));
        Files.writeString(productDir.resolve("broken.webp"), "not-an-image");

        CompanySettings settings = new CompanySettings();
        settings.setName("Maidav");
        when(companySettingsService.getSettings()).thenReturn(settings);

        QuoteDocumentServiceImpl service = new QuoteDocumentServiceImpl(
                productRepository,
                companySettingsService,
                quoteCalculator
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        Quote quote = new Quote();
        quote.setQuoteNumber("P-000002");
        quote.setPriceMode(QuotePriceMode.RETAIL);
        quote.setItemCount(1);
        quote.setProductSummary("Producto de prueba");
        quote.setPricingBaseAmount(new BigDecimal("100.00"));
        quote.setFinancingBaseAmount(new BigDecimal("100.00"));
        quote.setCashAmount(new BigDecimal("130.00"));
        quote.setDebitAmount(new BigDecimal("150.00"));
        quote.setTotalAmount(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(quote, "createdAt", LocalDateTime.of(2026, 4, 13, 10, 30));

        QuoteItem item = new QuoteItem();
        item.setDisplayOrder(1);
        item.setProductCode("SKU-1");
        item.setProductDescription("Producto con imagen no soportada");
        item.setProductImagePath("/uploads/products/broken.webp");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setLineTotal(new BigDecimal("100.00"));
        quote.addItem(item);

        byte[] pdf = service.generateQuotePdf(quote);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void loadImageSupportsWebpAndFitsInsidePdfFrame() throws Exception {
        Path productDir = Files.createDirectories(tempDir.resolve("products"));
        Path webp = productDir.resolve("sample.webp");

        BufferedImage source = new BufferedImage(220, 110, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 220, 110);
            graphics.setColor(new Color(15, 94, 118));
            graphics.fillRect(20, 15, 180, 80);
            graphics.setColor(new Color(179, 118, 64));
            graphics.fillOval(75, 20, 70, 70);
        } finally {
            graphics.dispose();
        }

        assertThat(ImageIO.write(source, "webp", webp.toFile())).isTrue();

        QuoteDocumentServiceImpl service = new QuoteDocumentServiceImpl(
                productRepository,
                companySettingsService,
                quoteCalculator
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        com.lowagie.text.Image image = ReflectionTestUtils.invokeMethod(
                service,
                "loadImage",
                "/uploads/products/sample.webp",
                92f,
                92f
        );

        assertThat(image).isNotNull();
        assertThat(image.getScaledWidth()).isBetween(91f, 92.5f);
        assertThat(image.getScaledHeight()).isBetween(91f, 92.5f);
    }
}
