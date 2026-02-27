package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.user.Permission;
import com.sales.maidav.model.user.Role;
import com.sales.maidav.repository.user.PermissionRepository;
import com.sales.maidav.repository.user.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public String list(Model model) {
        model.addAttribute("roles", roleRepository.findAll());
        return "pages/roles/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("role", new Role());
        model.addAttribute("permissions", permissionRepository.findAll());
        return "pages/roles/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public String create(Role role,
                         @RequestParam(required = false) List<Long> permissionIds) {

        if (permissionIds != null) {
            role.setPermissions(
                    new HashSet<>(permissionRepository.findAllById(permissionIds))
            );
        }

        roleRepository.save(role);
        return "redirect:/roles";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {

        model.addAttribute("role", roleRepository.findById(id).orElseThrow());
        model.addAttribute("permissions", permissionRepository.findAll());

        return "pages/roles/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public String update(@PathVariable Long id,
                         Role data,
                         @RequestParam(required = false) List<Long> permissionIds) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        role.setName(data.getName());

        if (permissionIds != null) {
            Set<Permission> actuales = role.getPermissions();
            Set<Permission> nuevos = new HashSet<>(
                    permissionRepository.findAllById(permissionIds)
            );

            actuales.addAll(nuevos); // 🔥 MERGE, no replace
        }

        roleRepository.save(role);
        return "redirect:/roles";
    }



    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public String delete(@PathVariable Long id) {
        roleRepository.deleteById(id);
        return "redirect:/roles";
    }
}
