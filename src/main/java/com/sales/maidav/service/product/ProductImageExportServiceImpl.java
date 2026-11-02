package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.repository.product.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProductImageExportServiceImpl implements ProductImageExportService {
    private static final String IMAGE_PREFIX = "/uploads/products/";
    private final ProductRepository productRepository;
    private final Path uploadDirectory;

    public ProductImageExportServiceImpl(ProductRepository productRepository,
                                        @Value("${app.upload-dir:uploads}") String uploadDirectory) {
        this.productRepository = productRepository;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void writeZip(OutputStream output) throws IOException {
        Set<String> names = new HashSet<>();
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Product product : productRepository.findAll()) {
                if (product.getImagePath() == null || product.getImagePath().isBlank()) {
                    continue;
                }
                Path image = resolveImage(product);
                String originalName = image.getFileName().toString();
                int dot = originalName.lastIndexOf('.');
                String extension = dot < 0 ? "" : originalName.substring(dot);
                if (!extension.matches("\\.[a-zA-Z0-9]{1,10}")) {
                    extension = "";
                }
                String base = safePart(product.getProductCode(), 70) + " - "
                        + safePart(product.getDescription(), 140);
                String name = base + extension;
                int suffix = 2;
                while (!names.add(name.toLowerCase(Locale.ROOT))) {
                    name = base + " (" + suffix++ + ")" + extension;
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(image, zip);
                zip.closeEntry();
                count++;
            }
            if (count == 0) {
                throw new InvalidProductException("No hay imágenes de productos para exportar.");
            }
        }
    }

    private Path resolveImage(Product product) {
        try {
            String imagePath = product.getImagePath();
            if (!imagePath.startsWith(IMAGE_PREFIX)) {
                throw new IOException("Ruta de imagen no permitida");
            }
            Path root = uploadDirectory.toRealPath();
            Path products = root.resolve("products");
            Path candidate = products.resolve(imagePath.substring(IMAGE_PREFIX.length())).normalize();
            if (!candidate.startsWith(products)) {
                throw new IOException("Ruta fuera del directorio de productos");
            }
            Path image = candidate.toRealPath();
            if (!image.startsWith(products) || !Files.isRegularFile(image) || !Files.isReadable(image)) {
                throw new IOException("Imagen no disponible");
            }
            return image;
        } catch (IOException | RuntimeException ex) {
            throw new InvalidProductException("No se pudo leer la imagen del producto "
                    + product.getProductCode() + ". Revise su imagen antes de volver a exportar.");
        }
    }

    private String safePart(String value, int limit) {
        String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ").strip().replaceAll("[. ]+$", "");
        if (safe.isEmpty()) {
            return "sin-dato";
        }
        if (safe.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")) {
            safe = "_" + safe;
        }
        // Keep extracted filenames within common filesystem byte limits, including Unicode.
        while (safe.getBytes(StandardCharsets.UTF_8).length > limit) {
            safe = safe.substring(0, safe.offsetByCodePoints(safe.length(), -1));
        }
        return safe.replaceAll("[. ]+$", "");
    }
}
