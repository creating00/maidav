package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.service.settings.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CompanySettingsService companySettingsService;
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    public String view(Model model) {
        model.addAttribute("settings", companySettingsService.getSettings());
        return "pages/settings/index";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    public String save(CompanySettings settings,
                       @RequestParam(required = false) MultipartFile logo,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        try {
            if (logo != null && !logo.isEmpty()) {
                attachLogo(settings, logo);
            } else if (settings.getId() != null) {
                CompanySettings existing = companySettingsService.getSettings();
                settings.setLogoPath(existing.getLogoPath());
            }
            companySettingsService.save(settings);
            redirectAttributes.addFlashAttribute("successMessage", "Configuracion guardada correctamente");
            return "redirect:/settings";
        } catch (RuntimeException ex) {
            model.addAttribute("settings", settings);
            model.addAttribute("formError", ex.getMessage());
            return "pages/settings/index";
        }
    }

    private void attachLogo(CompanySettings settings, MultipartFile logo) {
        String fileName = UUID.randomUUID() + "-" + logo.getOriginalFilename();
        Path companyUploadDir = Paths.get(uploadDir, "company");
        Path target = companyUploadDir.resolve(fileName);
        try {
            Files.createDirectories(companyUploadDir);
            Files.write(target, logo.getBytes());
            settings.setLogoPath("/uploads/company/" + fileName);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el logo");
        }
    }
}
