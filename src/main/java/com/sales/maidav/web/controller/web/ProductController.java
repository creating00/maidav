package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.ProductPriceAdjustment;
import com.sales.maidav.model.product.PriceAdjustmentScope;
import com.sales.maidav.model.product.PriceAdjustmentType;
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
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // FIX COSTO PRODUCTO
        binder.registerCustomEditor(BigDecimal.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null) {
                    setValue(null);
                    return;
                }
                String normalized = text.trim().replace(" ", "");
                if (normalized.isEmpty()) {
                    setValue(null);
                    return;
                }
                normalized = normalized.replace("$", "");
                normalized = normalizeDecimalValue(normalized);
                try {
                    setValue(new BigDecimal(normalized));
                } catch (NumberFormatException ex) {
                    setValue(null);
                }
            }
        });
    }

    private String normalizeDecimalValue(String rawValue) {
        String normalized = rawValue;
        boolean hasComma = normalized.contains(",");
        boolean hasDot = normalized.contains(".");

        if (hasComma && hasDot) {
            if (normalized.lastIndexOf(',') > normalized.lastIndexOf('.')) {
                return normalized.replace(".", "").replace(",", ".");
            }
            return normalized.replace(",", "");
        }

        if (hasComma) {
            int commaIndex = normalized.lastIndexOf(',');
            int decimalDigits = normalized.length() - commaIndex - 1;
            if (decimalDigits == 3 && normalized.indexOf(',') == commaIndex) {
                return normalized.replace(",", "");
            }
            return normalized.replace(".", "").replace(",", ".");
        }

        if (hasDot) {
            int dotIndex = normalized.lastIndexOf('.');
            int decimalDigits = normalized.length() - dotIndex - 1;
            if (decimalDigits == 3 && normalized.indexOf('.') == dotIndex) {
                return normalized.replace(".", "");
            }
        }

        return normalized;
    }

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
                       @RequestParam(required = false) String updateAgeFilter,
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
        products = filterByUpdateAge(products, updateAgeFilter);
        model.addAttribute("q", q);
        model.addAttribute("providerId", providerId);
        model.addAttribute("updateAgeFilter", updateAgeFilter);
        model.addAttribute("products", products);
        model.addAttribute("providers", providerService.findAll());
        model.addAttribute("adjustmentTypes", PriceAdjustmentType.values());
        List<ProductPriceAdjustment> adjustments = productService.findRecentAdjustments();
        model.addAttribute("adjustments", adjustments);
        model.addAttribute("adjustmentProductCodes", productService.findAdjustmentProductCodes(
                adjustments.stream().map(ProductPriceAdjustment::getId).toList()
        ));
        model.addAttribute("focusProductId", productId);
        model.addAttribute("calculatorConfig", buildCalculatorConfig());
        return "pages/products/index";
    }

    private List<Product> filterByUpdateAge(List<Product> products, String updateAgeFilter) {
        if (products == null || products.isEmpty() || updateAgeFilter == null || updateAgeFilter.isBlank()) {
            return products;
        }
        LocalDateTime now = LocalDateTime.now();
        return switch (updateAgeFilter.trim().toUpperCase(Locale.ROOT)) {
            case "RECENT_15" -> products.stream()
                    .filter(product -> lastUpdatedAt(product).isAfter(now.minusDays(15)))
                    .toList();
            case "RECENT_30" -> products.stream()
                    .filter(product -> lastUpdatedAt(product).isAfter(now.minusDays(30)))
                    .toList();
            case "STALE_30" -> products.stream()
                    .filter(product -> !lastUpdatedAt(product).isAfter(now.minusDays(30)))
                    .toList();
            case "STALE_60" -> products.stream()
                    .filter(product -> !lastUpdatedAt(product).isAfter(now.minusDays(60)))
                    .toList();
            default -> products;
        };
    }

    private LocalDateTime lastUpdatedAt(Product product) {
        if (product == null) {
            return LocalDateTime.MIN;
        }
        return product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt();
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
                               @RequestParam(defaultValue = "INCREASE") PriceAdjustmentType adjustmentType,
                               @RequestParam(defaultValue = "ALL") PriceAdjustmentScope scope,
                               RedirectAttributes redirectAttributes) {
        try {
            long updated = productService.bulkAdjustPrices(percentage, providerId, adjustmentType, scope);
            redirectAttributes.addFlashAttribute("successMessage", "Precios actualizados: " + updated + " producto(s)");
        } catch (InvalidProductException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if (providerId != null) {
            return "redirect:/products?providerId=" + providerId;
        }
        return "redirect:/products";
    }

    @PostMapping("/adjustments/{id}/undo")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String undoAdjustment(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            productService.undoAdjustment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Ajuste deshecho correctamente");
        } catch (InvalidProductException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/products";
    }

    @GetMapping("/barcode/generate")
    @ResponseBody
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE') or hasAuthority('PRODUCT_CREATE')")
    public Map<String, String> generateBarcode() {
        return Map.of("barcode", productService.generateSystemBarcode());
    }

    // RESTRICCION POR ROL
    @GetMapping(value = "/barcode/label", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_BARCODE_LABEL')")
    public String barcodeLabel(@RequestParam String barcode,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String productCode,
                               Model model) {
        model.addAttribute("barcode", barcode);
        model.addAttribute("description", description);
        model.addAttribute("productCode", productCode);
        model.addAttribute("barcodeSvg", productService.renderBarcodeLabelSvg(barcode));
        return "pages/products/barcode-label";
    }

    @GetMapping(value = "/barcode/sheet", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_BARCODE_SHEET')")
    public String barcodeSheet(@RequestParam String barcode,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String productCode,
                               @RequestParam(defaultValue = "12") Integer copies,
                               Model model) {
        int safeCopies = copies == null ? 12 : Math.max(1, Math.min(copies, 65));
        List<Integer> labels = java.util.stream.IntStream.range(0, safeCopies).boxed().toList();
        model.addAttribute("barcode", barcode);
        model.addAttribute("description", description);
        model.addAttribute("productCode", productCode);
        model.addAttribute("copies", safeCopies);
        model.addAttribute("labels", labels);
        model.addAttribute("barcodeSvg", productService.renderBarcodeLabelSvg(barcode));
        return "pages/products/barcode-sheet";
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

