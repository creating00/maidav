package com.sales.maidav.web.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AccessDeniedController {

    @GetMapping("/access-denied")
    public String accessDenied(@RequestParam(required = false) String message,
                               HttpServletRequest request,
                               Model model) {
        String accessDeniedMessage = (String) request.getAttribute("accessDeniedMessage");
        model.addAttribute("message", accessDeniedMessage != null && !accessDeniedMessage.isBlank()
                ? accessDeniedMessage
                : message != null && !message.isBlank()
                ? message
                : "No tienes acceso");
        return "pages/error/access-denied";
    }
}
