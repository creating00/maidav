package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductImageExportServiceImplTest {
    @TempDir Path uploads;
    private final ProductRepository repository = mock(ProductRepository.class);
    private ProductImageExportService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectory(uploads.resolve("products"));
        service = new ProductImageExportServiceImpl(repository, uploads.toString());
    }

    @Test
    void exportsCurrentImagesIncludingOutOfStockAndSkipsProductsWithoutImage() throws Exception {
        writeImage("current.jpg", "jpg");
        byte[] original = Files.readAllBytes(uploads.resolve("products/current.jpg"));
        Files.writeString(uploads.resolve("products/old.jpg"), "obsolete");
        Product product = product("PRD-KDGAJ5PUM", "Colchón matrimonial", "current.jpg");
        product.setStockAvailable(0);
        when(repository.findAll()).thenReturn(List.of(product, product("P002", "Sin imagen", null)));

        Map<String, byte[]> entries = export();

        assertThat(entries).containsOnlyKeys("PRD-KDGAJ5PUM.jpg");
        assertJpeg(entries.get("PRD-KDGAJ5PUM.jpg"));
        assertThat(Files.readAllBytes(uploads.resolve("products/current.jpg"))).isEqualTo(original);
        assertThat(Files.readString(uploads.resolve("products/old.jpg"))).isEqualTo("obsolete");
    }

    @Test
    void createsSafeDistinctNamesEvenWithCaseInsensitiveCollisions() throws Exception {
        writeImage("a.PNG", "png");
        when(repository.findAll()).thenReturn(List.of(
                product("P/1", "Mesa: grande?", "a.PNG"),
                product("p\\1", "mesa: grande?", "a.PNG")));

        assertThat(export()).containsOnlyKeys("P_1.jpg", "p_1 (2).jpg");
    }

    @Test
    void avoidsReservedWindowsNamesAndIgnoresDescription() throws Exception {
        writeImage("a.jpg", "jpg");
        when(repository.findAll()).thenReturn(List.of(product("CON.code", "Colchón".repeat(100), "a.jpg")));
        String name = export().keySet().iterator().next();
        assertThat(name).isEqualTo("_CON.code.jpg");
        assertThat(name.getBytes(StandardCharsets.UTF_8).length).isLessThan(255);
    }

    @Test
    void reportsEmptyCatalogAndProductsWithoutImages() {
        when(repository.findAll()).thenReturn(List.of(product("P001", "Sin imagen", null)));
        assertThatThrownBy(() -> export()).isInstanceOf(InvalidProductException.class)
                .hasMessageContaining("No hay imágenes");
    }

    @Test
    void skipsMissingImagesAndUnsafePathsAndContinuesWithAvailableImages() throws Exception {
        Files.writeString(uploads.resolve("secret.jpg"), "private");
        writeImage("available.jpg", "jpg");
        Files.writeString(uploads.resolve("products/broken.webp"), "invalid image content");
        Product available = product("P002", "Disponible", "available.jpg");
        for (String path : List.of("missing.jpg", "../secret.jpg", "../../secret.jpg", "broken.webp")) {
            when(repository.findAll()).thenReturn(List.of(product("P001", "Producto", path), available));
            Map<String, byte[]> entries = export();
            assertThat(entries).containsOnlyKeys("P002.jpg");
            assertJpeg(entries.get("P002.jpg"));
        }
        Product external = product("P003", "Externo", null);
        external.setImagePath("https://example.com/image.jpg");
        when(repository.findAll()).thenReturn(List.of(external, available));
        assertThat(export()).containsOnlyKeys("P002.jpg");
    }

    @Test
    void reportsNoImagesWhenEveryRegisteredImageIsMissing() {
        when(repository.findAll()).thenReturn(List.of(product("PRD-9CKQK05H0", "Producto", "missing.jpg")));
        assertThatThrownBy(() -> export()).isInstanceOf(InvalidProductException.class)
                .hasMessage("No hay imágenes de productos para exportar.");
    }

    @Test
    void convertsWebpAndPngToRealJpegWithoutChangingOriginals() throws Exception {
        for (String format : List.of("webp", "png")) {
            String file = "source." + format;
            writeImage(file, format);
            Path originalPath = uploads.resolve("products").resolve(file);
            byte[] original = Files.readAllBytes(originalPath);
            when(repository.findAll()).thenReturn(List.of(product("PRD-KDGAJ5PUM", "Producto", file)));

            Map<String, byte[]> entries = export();

            assertThat(entries).containsOnlyKeys("PRD-KDGAJ5PUM.jpg");
            assertJpeg(entries.get("PRD-KDGAJ5PUM.jpg"));
            assertThat(Files.readAllBytes(originalPath)).isEqualTo(original);
        }
    }

    @Test
    void convertsTransparentPixelsToWhiteBackground() throws Exception {
        BufferedImage transparent = new BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB);
        assertThat(ImageIO.write(transparent, "png", uploads.resolve("products/transparent.png").toFile())).isTrue();
        when(repository.findAll()).thenReturn(List.of(product("P001", "Transparente", "transparent.png")));

        byte[] jpeg = export().get("P001.jpg");
        assertJpeg(jpeg);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(new Color(decoded.getRGB(8, 6))).isEqualTo(Color.WHITE);
    }

    private void writeImage(String file, String format) throws Exception {
        BufferedImage image = new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }
        assertThat(ImageIO.write(image, format, uploads.resolve("products").resolve(file).toFile())).isTrue();
    }

    private void assertJpeg(byte[] bytes) throws Exception {
        assertThat(bytes).startsWith((byte) 0xff, (byte) 0xd8, (byte) 0xff);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(16);
        assertThat(decoded.getHeight()).isEqualTo(12);
    }

    private Map<String, byte[]> export() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private Product product(String code, String description, String file) {
        Product product = new Product();
        product.setProductCode(code);
        product.setDescription(description);
        product.setImagePath(file == null ? null : "/uploads/products/" + file);
        return product;
    }
}
