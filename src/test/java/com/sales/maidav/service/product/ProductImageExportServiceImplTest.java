package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Files.write(uploads.resolve("products/current.jpg"), new byte[]{1, 2, 3});
        Files.writeString(uploads.resolve("products/old.jpg"), "obsolete");
        Product product = product("P001", "Colchón matrimonial", "current.jpg");
        product.setStockAvailable(0);
        when(repository.findAll()).thenReturn(List.of(product, product("P002", "Sin imagen", null)));

        Map<String, byte[]> entries = export();

        assertThat(entries).containsOnlyKeys("P001 - Colchón matrimonial.jpg");
        assertThat(entries.get("P001 - Colchón matrimonial.jpg")).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(uploads.resolve("products/current.jpg"))).containsExactly(1, 2, 3);
        assertThat(Files.readString(uploads.resolve("products/old.jpg"))).isEqualTo("obsolete");
    }

    @Test
    void createsSafeDistinctNamesEvenWithCaseInsensitiveCollisions() throws Exception {
        Files.writeString(uploads.resolve("products/a.PNG"), "image");
        when(repository.findAll()).thenReturn(List.of(
                product("P/1", "Mesa: grande?", "a.PNG"),
                product("p\\1", "mesa: grande?", "a.PNG")));

        assertThat(export()).containsOnlyKeys("P_1 - Mesa_ grande_.PNG", "p_1 - mesa_ grande_ (2).PNG");
    }

    @Test
    void limitsUnicodeNamesAndAvoidsReservedWindowsNames() throws Exception {
        Files.writeString(uploads.resolve("products/a.jpg"), "image");
        when(repository.findAll()).thenReturn(List.of(product("CON.code", "Colchón".repeat(100), "a.jpg")));
        String name = export().keySet().iterator().next();
        assertThat(name).startsWith("_CON.code - ").endsWith(".jpg");
        assertThat(name.getBytes(StandardCharsets.UTF_8).length).isLessThan(255);
    }

    @Test
    void reportsEmptyCatalogAndProductsWithoutImages() {
        when(repository.findAll()).thenReturn(List.of(product("P001", "Sin imagen", null)));
        assertThatThrownBy(() -> export()).isInstanceOf(InvalidProductException.class)
                .hasMessageContaining("No hay imágenes");
    }

    @Test
    void rejectsMissingImagesAndPathsOutsideProductDirectory() throws Exception {
        Files.writeString(uploads.resolve("secret.jpg"), "private");
        for (String path : List.of("missing.jpg", "../secret.jpg", "../../secret.jpg")) {
            when(repository.findAll()).thenReturn(List.of(product("P001", "Producto", path)));
            assertThatThrownBy(() -> export()).isInstanceOf(InvalidProductException.class)
                    .hasMessageContaining("P001");
        }
        Product external = product("P002", "Externo", null);
        external.setImagePath("https://example.com/image.jpg");
        when(repository.findAll()).thenReturn(List.of(external));
        assertThatThrownBy(() -> export()).isInstanceOf(InvalidProductException.class);
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
