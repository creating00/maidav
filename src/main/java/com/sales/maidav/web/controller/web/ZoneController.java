package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Zone;
import com.sales.maidav.service.client.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    @PreAuthorize("hasAuthority('ZONE_READ')")
    public String list(Model model) {
        model.addAttribute("zones", zoneService.findAll());
        return "pages/zones/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('ZONE_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("zone", new Zone());
        return "pages/zones/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ZONE_CREATE')")
    public String create(Zone zone, Model model, RedirectAttributes redirectAttributes) {
        try {
            zoneService.create(zone);
            redirectAttributes.addFlashAttribute("successMessage", "Zona guardada correctamente");
            return "redirect:/zones";
        } catch (RuntimeException ex) {
            model.addAttribute("zone", zone);
            model.addAttribute("formError", ex.getMessage());
            return "pages/zones/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('ZONE_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("zone", zoneService.findById(id));
        return "pages/zones/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('ZONE_UPDATE')")
    public String update(@PathVariable Long id, Zone zone, Model model, RedirectAttributes redirectAttributes) {
        try {
            zoneService.update(id, zone);
            redirectAttributes.addFlashAttribute("successMessage", "Zona guardada correctamente");
            return "redirect:/zones";
        } catch (RuntimeException ex) {
            zone.setId(id);
            model.addAttribute("zone", zone);
            model.addAttribute("formError", ex.getMessage());
            return "pages/zones/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('ZONE_DELETE')")
    public String delete(@PathVariable Long id) {
        zoneService.delete(id);
        return "redirect:/zones";
    }
}
