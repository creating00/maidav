package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.repository.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
    private static final Logger log = LoggerFactory.getLogger(ProductImageExportServiceImpl.class);
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
        Path stagedImage = Files.createTempFile("product-image-", ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Product product : productRepository.findAll()) {
                if (product.getImagePath() == null || product.getImagePath().isBlank()) {
                    continue;
                }
                try {
                    // Complete conversion before adding an entry, avoiding partial images in the ZIP.
                    convertToJpeg(resolveImage(product), stagedImage);
                } catch (InvalidProductException | IOException ex) {
                    log.warn("Se omite la imagen del producto {} durante la exportación: {}",
                            product.getProductCode(), ex.getMessage());
                    continue;
                }
                String base = safePart(product.getProductCode(), 70);
                String name = base + ".jpg";
                int suffix = 2;
                while (!names.add(name.toLowerCase(Locale.ROOT))) {
                    name = base + " (" + suffix++ + ").jpg";
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(stagedImage, zip);
                zip.closeEntry();
                count++;
            }
            if (count == 0) {
                throw new InvalidProductException("No hay imágenes de productos para exportar.");
            }
        } finally {
            Files.deleteIfExists(stagedImage);
        }
    }

    private void convertToJpeg(Path image, Path target) throws IOException {
        BufferedImage source = ImageIO.read(image.toFile());
        if (source == null) {
            throw new IOException("Formato de imagen no reconocido");
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        try {
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            try (OutputStream converted = Files.newOutputStream(target)) {
                if (!ImageIO.write(rgb, "jpg", converted)) {
                    throw new IOException("No se pudo convertir la imagen a JPG");
                }
            }
        } finally {
            source.flush();
            rgb.flush();
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
