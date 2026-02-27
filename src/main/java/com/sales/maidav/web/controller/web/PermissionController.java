package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.user.Permission;
import com.sales.maidav.service.user.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public String list(Model model) {
        model.addAttribute("permissions", permissionService.findAll());
        return "pages/permissions/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("permission", new Permission(""));
        return "pages/permissions/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public String create(Permission permission) {
        permissionService.create(permission);
        return "redirect:/permissions";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("permission", permissionService.findById(id));
        return "pages/permissions/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public String update(@PathVariable Long id, Permission permission) {
        permissionService.update(id, permission);
        return "redirect:/permissions";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    public String delete(@PathVariable Long id) {
        permissionService.delete(id);
        return "redirect:/permissions";
    }
}

