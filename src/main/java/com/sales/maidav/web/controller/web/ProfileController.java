package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.user.User;
import com.sales.maidav.service.user.PasswordRecoveryService;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final PasswordRecoveryService passwordRecoveryService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        User user = userService.findByEmail(authentication.getName());
        fillProfileModel(model, user, true, false);
        return "pages/profile/index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public String profileByUser(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        fillProfileModel(model, user, false, true);
        return "pages/profile/index";
    }

    @PostMapping
    public String updateProfile(Authentication authentication,
                                User form,
                                @RequestParam(required = false) MultipartFile photo,
                                Model model) {
        User user = userService.findByEmail(authentication.getName());
        applyProfileUpdates(user, form, photo, model);
        if (model.containsAttribute("errorMessage")) {
            fillProfileModel(model, user, true, false);
            return "pages/profile/index";
        }
        userService.updateProfile(user);
        fillProfileModel(model, user, true, false);
        model.addAttribute("successMessage", "Perfil actualizado correctamente");
        return "pages/profile/index";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public String updateProfileByAdmin(@PathVariable Long id,
                                       User form,
                                       @RequestParam(required = false) MultipartFile photo,
                                       Model model) {
        User user = userService.findById(id);
        applyProfileUpdates(user, form, photo, model);
        if (model.containsAttribute("errorMessage")) {
            fillProfileModel(model, user, false, true);
            return "pages/profile/index";
        }
        userService.updateProfile(user);
        fillProfileModel(model, user, false, true);
        model.addAttribute("successMessage", "Datos personales actualizados correctamente");
        return "pages/profile/index";
    }

    @PostMapping("/password")
    public String changePassword(Authentication authentication,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.changePassword(authentication.getName(), currentPassword, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Contrasena actualizada correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/{id}/recovery-token")
    @PreAuthorize("hasAuthority('USER_READ')")
    public String generateUserRecoveryToken(@PathVariable Long id,
                                            RedirectAttributes redirectAttributes) {
        try {
            PasswordRecoveryService.RecoveryTokenResult result = passwordRecoveryService.generateRecoveryTokenForUser(id);
            redirectAttributes.addFlashAttribute("generatedRecoveryToken", result.token());
            redirectAttributes.addFlashAttribute("generatedRecoveryTokenExpiresAt", result.expiresAt());
            redirectAttributes.addFlashAttribute("generatedRecoveryTokenUserLabel", result.userLabel());
            redirectAttributes.addFlashAttribute("successMessage", "Token temporal generado correctamente");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/profile/" + id;
    }

    private void applyProfileUpdates(User user, User form, MultipartFile photo, Model model) {
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setPhone(form.getPhone());
        user.setAddress(form.getAddress());
        user.setNationalId(form.getNationalId());
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
                model.addAttribute("errorMessage", "No se pudo guardar la foto");
            }
        }
    }

    private void fillProfileModel(Model model, User user, boolean ownProfile, boolean viewingAnotherUser) {
        model.addAttribute("user", user);
        model.addAttribute("age", calculateAge(user.getBirthDate()));
        model.addAttribute("ownProfile", ownProfile);
        model.addAttribute("viewingAnotherUser", viewingAnotherUser);
        model.addAttribute("profileFormAction", ownProfile ? "/profile" : "/profile/" + user.getId());
        model.addAttribute("recoveryTokenAction", "/profile/" + user.getId() + "/recovery-token");
        model.addAttribute("backUrl", viewingAnotherUser ? "/users" : "/dashboard");
        model.addAttribute("backLabel", viewingAnotherUser ? "Volver a usuarios" : "Volver al dashboard");
    }

    private Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
