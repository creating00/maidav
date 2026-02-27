package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.user.User;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    public String profile(org.springframework.security.core.Authentication authentication, Model model) {
        User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        model.addAttribute("age", calculateAge(user.getBirthDate()));
        return "pages/profile/index";
    }

    @PostMapping
    public String updateProfile(org.springframework.security.core.Authentication authentication,
                                User form,
                                @RequestParam(required = false) MultipartFile photo,
                                Model model) {
        User user = userService.findByEmail(authentication.getName());

        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setPhone(form.getPhone());
        user.setAddress(form.getAddress());
        user.setBirthDate(form.getBirthDate());

        if (photo != null && !photo.isEmpty()) {
            String fileName = UUID.randomUUID() + "-" + photo.getOriginalFilename();
            Path userUploadDir = Paths.get(uploadDir);
            Path target = userUploadDir.resolve(fileName);
            try {
                Files.createDirectories(userUploadDir);
                Files.write(target, photo.getBytes());
                user.setPhotoPath("/uploads/" + fileName);
            } catch (IOException e) {
                model.addAttribute("user", user);
                model.addAttribute("age", calculateAge(user.getBirthDate()));
                model.addAttribute("errorMessage", "No se pudo guardar la foto");
                return "pages/profile/index";
            }
        }

        userService.updateProfile(user);
        model.addAttribute("user", user);
        model.addAttribute("age", calculateAge(user.getBirthDate()));
        model.addAttribute("successMessage", "Perfil actualizado correctamente");
        return "pages/profile/index";
    }

    private Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
