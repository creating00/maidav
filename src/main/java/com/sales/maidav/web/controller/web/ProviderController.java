package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.product.Provider;
import com.sales.maidav.service.product.InvalidProductException;
import com.sales.maidav.service.product.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    public String list(Model model) {
        model.addAttribute("providers", providerService.findAll());
        return "pages/providers/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PROVIDER_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("provider", new Provider());
        return "pages/providers/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROVIDER_CREATE')")
    public String create(Provider provider, Model model, RedirectAttributes redirectAttributes) {
        try {
            providerService.create(provider);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor guardado correctamente");
            return "redirect:/providers";
        } catch (InvalidProductException ex) {
            model.addAttribute("provider", provider);
            model.addAttribute("formError", ex.getMessage());
            return "pages/providers/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PROVIDER_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("provider", providerService.findById(id));
        return "pages/providers/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVIDER_UPDATE')")
    public String update(@PathVariable Long id, Provider provider, Model model, RedirectAttributes redirectAttributes) {
        try {
            providerService.update(id, provider);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor guardado correctamente");
            return "redirect:/providers";
        } catch (InvalidProductException ex) {
            provider.setId(id);
            model.addAttribute("provider", provider);
            model.addAttribute("formError", ex.getMessage());
            return "pages/providers/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PROVIDER_DELETE')")
    public String delete(@PathVariable Long id) {
        providerService.delete(id);
        return "redirect:/providers";
    }
}
