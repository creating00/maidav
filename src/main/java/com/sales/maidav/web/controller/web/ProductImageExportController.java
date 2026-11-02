package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.product.InvalidProductException;
import com.sales.maidav.service.product.ProductImageExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class ProductImageExportController {
    private static final Logger log = LoggerFactory.getLogger(ProductImageExportController.class);
    private final ProductImageExportService productImageExportService;

    @GetMapping("/products/images/export")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public String exportImages(HttpServletResponse response, RedirectAttributes redirectAttributes) throws IOException {
        Path archive = null;
        try {
            // Finish on disk before sending headers so generation errors can redirect to Products.
            try {
                archive = Files.createTempFile("product-images-", ".zip");
                try (OutputStream output = Files.newOutputStream(archive)) {
                    productImageExportService.writeZip(output);
                }
            } catch (InvalidProductException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/products";
            } catch (IOException ex) {
                log.error("No se pudo preparar la exportación de imágenes", ex);
                redirectAttributes.addFlashAttribute("errorMessage", "No se pudo generar el ZIP de imágenes. Intente nuevamente.");
                return "redirect:/products";
            }
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=imagenes-productos-" + LocalDate.now() + ".zip");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentLengthLong(Files.size(archive));
            Files.copy(archive, response.getOutputStream());
            return null;
        } finally {
            if (archive != null) {
                Files.deleteIfExists(archive);
            }
        }
    }
}
