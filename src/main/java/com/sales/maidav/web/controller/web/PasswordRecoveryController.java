package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.user.PasswordRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordRecoveryController {

    private final PasswordRecoveryService passwordRecoveryService;

    @GetMapping("/password-recovery")
    public String recoveryEntryPoint() {
        return "redirect:/password-recovery/reset";
    }

    @GetMapping("/password-recovery/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "pages/password-recovery-reset";
    }

    @PostMapping("/password-recovery/reset")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            passwordRecoveryService.resetPassword(token, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "La contrasena se actualizo correctamente");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("token", token);
            return "redirect:/password-recovery/reset";
        }
    }
}
