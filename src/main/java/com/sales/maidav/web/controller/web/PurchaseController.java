package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.product.ProviderService;
import com.sales.maidav.service.purchase.InvalidPurchaseException;
import com.sales.maidav.service.purchase.PurchaseService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.PurchaseForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {
    private final PurchaseService purchaseService;
    private final ProductService productService;
    private final ProviderService providerService;
    private final UserService userService;

    @InitBinder("purchaseForm")
    void configureBinding(WebDataBinder binder) {
        binder.setAllowedFields("purchaseDate", "providerId", "paymentMethod", "notes", "operationKey",
                "items[*].productId", "items[*].quantity", "items[*].unitPrice");
        binder.setAutoGrowCollectionLimit(100);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long providerId,
                       @RequestParam(required = false) Long productId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        try {
            var purchases = purchaseService.findPage(from, to, providerId, productId, page);
            if (purchases.getTotalPages() > 0 && page >= purchases.getTotalPages()) {
                purchases = purchaseService.findPage(from, to, providerId, productId, purchases.getTotalPages() - 1);
            }
            model.addAttribute("purchases", purchases);
        } catch (InvalidPurchaseException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("purchases", Page.empty());
        }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("providerId", providerId);
        model.addAttribute("productId", productId);
        addOptions(model);
        return "pages/purchases/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public String createForm(Model model) {
        PurchaseForm form = new PurchaseForm();
        form.setPurchaseDate(LocalDate.now());
        form.setOperationKey(UUID.randomUUID().toString());
        form.getItems().add(new PurchaseForm.Item());
        model.addAttribute("purchaseForm", form);
        addOptions(model);
        return "pages/purchases/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public String create(@ModelAttribute PurchaseForm purchaseForm, BindingResult errors,
                         Authentication authentication, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("formError", "Revise los campos: fecha, producto, cantidad y precio deben ser válidos");
        } else {
            try {
                var user = userService.findByEmail(authentication.getName());
                var purchase = purchaseService.create(purchaseForm, user.getId());
                redirect.addFlashAttribute("successMessage", "Compra registrada. El stock fue actualizado");
                // Creation-only users can complete the operation without needing read permission.
                boolean canRead = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("PURCHASE_READ"));
                return canRead ? "redirect:/purchases/" + purchase.getId() : "redirect:/purchases/new";
            } catch (InvalidPurchaseException ex) {
                model.addAttribute("formError", ex.getMessage());
            }
        }
        if (purchaseForm.getItems() == null || purchaseForm.getItems().isEmpty()) {
            purchaseForm.setItems(new java.util.ArrayList<>());
            purchaseForm.getItems().add(new PurchaseForm.Item());
        }
        addOptions(model);
        return "pages/purchases/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("purchase", purchaseService.findById(id));
        model.addAttribute("paymentMethods", paymentMethods());
        return "pages/purchases/detail";
    }

    private void addOptions(Model model) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("providers", providerService.findAll());
        model.addAttribute("paymentMethods", paymentMethods());
        model.addAttribute("today", LocalDate.now());
    }

    private java.util.Map<PaymentCollectionMethod, String> paymentMethods() {
        var methods = new java.util.LinkedHashMap<PaymentCollectionMethod, String>();
        methods.put(PaymentCollectionMethod.CASH, "Efectivo");
        methods.put(PaymentCollectionMethod.BANK, "Banco");
        methods.put(PaymentCollectionMethod.TRANSFER, "Transferencia");
        methods.put(PaymentCollectionMethod.DEBIT, "Débito");
        return methods;
    }
}
