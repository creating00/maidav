package com.sales.maidav.web.controller.web;
import org.springframework.ui.Model;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.user.RoleRepository;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "pages/users/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleRepository.findAll());
        return "pages/users/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public String create(User user,
                         @RequestParam List<Long> roleIds) {

        userService.create(user, roleIds);
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {

        model.addAttribute("user", userService.findById(id));
        model.addAttribute("roles", roleRepository.findAll());
        return "pages/users/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public String update(@PathVariable Long id,
                         User user,
                         @RequestParam List<Long> roleIds) {

        userService.update(id, user, roleIds);
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public String delete(@PathVariable Long id) {

        userService.delete(id);
        return "redirect:/users";
    }
}
