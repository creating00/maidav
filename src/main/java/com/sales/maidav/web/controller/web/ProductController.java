package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.service.product.DuplicateProductCodeException;
import com.sales.maidav.service.product.InvalidProductException;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.product.ProviderService;
import com.sales.maidav.service.settings.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProviderService providerService;
    private final CompanySettingsService companySettingsService;
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public String list(@RequestParam(required = false) Boolean lowStock,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) Long providerId,
                       @RequestParam(required = false) Long productId,
                       Model model) {
        List<Product> products;
        if (Boolean.TRUE.equals(lowStock)) {
            products = productService.findLowStock();
            model.addAttribute("lowStockOnly", true);
        } else {
            products = productService.findAll();
        }
        if (providerId != null) {
            products = products.stream()
                    .filter(p -> p.getProvider() != null && providerId.equals(p.getProvider().getId()))
                    .toList();
        }
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            products = products.stream()
                    .filter(p -> contains(p.getProductCode(), term)
                            || contains(p.getBarcode(), term)
                            || contains(p.getDescription(), term)
                            || (p.getProvider() != null && contains(p.getProvider().getName(), term)))
                    .toList();
        }
        model.addAttribute("q", q);
        model.addAttribute("providerId", providerId);
        model.addAttribute("products", products);
        model.addAttribute("providers", providerService.findAll());
        model.addAttribute("focusProductId", productId);
        model.addAttribute("calculatorConfig", buildCalculatorConfig());
        return "pages/products/index";
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private Map<String, Object> buildCalculatorConfig() {
        CompanySettings settings = companySettingsService.getSettings();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("recargo", getDecimal(settings.getCalcRecargo(), "1.26"));
        cfg.put("multContado", getDecimal(settings.getCalcMultContado(), "1.30"));
        cfg.put("multDebito", getDecimal(settings.getCalcMultDebito(), "1.50"));
        cfg.put("dias", getInt(settings.getCalcDias(), 144));
        cfg.put("intDia", getDecimal(settings.getCalcIntDia(), "2.00"));
        cfg.put("semanas", getInt(settings.getCalcSemanas(), 13));
        cfg.put("intSem", getDecimal(settings.getCalcIntSem(), "2.00"));
        cfg.put("mesesCorto", getInt(settings.getCalcMesesCorto(), 4));
        cfg.put("intMesCorto", getDecimal(settings.getCalcIntMesCorto(), "2.00"));
        cfg.put("mesesLargo", getInt(settings.getCalcMesesLargo(), 8));
        cfg.put("intMesLargo", getDecimal(settings.getCalcIntMesLargo(), "2.50"));
        return cfg;
    }

    private BigDecimal getDecimal(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    private Integer getInt(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public String createForm(Model model) {
        Product product = new Product();
        model.addAttribute("product", product);
        model.addAttribute("providers", providerService.findAll());
        return "pages/products/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public String create(Product product,
                         @RequestParam(required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            attachImage(product, image);
            productService.create(product);
            redirectAttributes.addFlashAttribute("successMessage", "Producto guardado correctamente");
            return "redirect:/products";
        } catch (InvalidProductException | DuplicateProductCodeException ex) {
            model.addAttribute("product", product);
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("providers", providerService.findAll());
            return "pages/products/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = productService.findById(id);
        model.addAttribute("product", product);
        model.addAttribute("providers", providerService.findAll());
        return "pages/products/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String update(@PathVariable Long id,
                         Product product,
                         @RequestParam(required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            if (image == null || image.isEmpty()) {
                Product existing = productService.findById(id);
                product.setImagePath(existing.getImagePath());
            } else {
                attachImage(product, image);
            }
            productService.update(id, product);
            redirectAttributes.addFlashAttribute("successMessage", "Producto guardado correctamente");
            return "redirect:/products";
        } catch (InvalidProductException | DuplicateProductCodeException ex) {
            product.setId(id);
            model.addAttribute("product", product);
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("providers", providerService.findAll());
            return "pages/products/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public String delete(@PathVariable Long id) {
        productService.delete(id);
        return "redirect:/products";
    }

    @PostMapping("/bulk-increase")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String bulkIncrease(@RequestParam BigDecimal percentage,
                               @RequestParam(required = false) Long providerId,
                               RedirectAttributes redirectAttributes) {
        try {
            long updated = productService.bulkIncreasePrices(percentage, providerId);
            redirectAttributes.addFlashAttribute("successMessage", "Precios actualizados: " + updated + " producto(s)");
        } catch (InvalidProductException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if (providerId != null) {
            return "redirect:/products?providerId=" + providerId;
        }
        return "redirect:/products";
    }

    private void attachImage(Product product, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        String fileName = UUID.randomUUID() + "-" + image.getOriginalFilename();
        Path productUploadDir = Paths.get(uploadDir, "products");
        Path target = productUploadDir.resolve(fileName);
        try {
            Files.createDirectories(productUploadDir);
            Files.write(target, image.getBytes());
            product.setImagePath("/uploads/products/" + fileName);
        } catch (IOException e) {
            throw new InvalidProductException("No se pudo guardar la imagen");
        }
    }
}
