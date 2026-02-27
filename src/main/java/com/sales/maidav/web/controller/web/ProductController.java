package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.service.product.DuplicateProductCodeException;
import com.sales.maidav.service.product.InvalidProductException;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.product.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProviderService providerService;
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public String list(@RequestParam(required = false) Boolean lowStock, Model model) {
        if (Boolean.TRUE.equals(lowStock)) {
            model.addAttribute("products", productService.findLowStock());
            model.addAttribute("lowStockOnly", true);
        } else {
            model.addAttribute("products", productService.findAll());
        }
        return "pages/products/index";
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
