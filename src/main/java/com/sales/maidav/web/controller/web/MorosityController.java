package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.MorosityLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/arrears")
@RequiredArgsConstructor
public class MorosityController {

    private final CreditAccountService creditAccountService;

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String list(@RequestParam(required = false) String level, Model model) {
        MorosityLevel filter = null;
        if (level != null && !level.isBlank()) {
            filter = MorosityLevel.valueOf(level.toUpperCase());
        }
        model.addAttribute("level", level == null ? "" : level);
        model.addAttribute("rows", creditAccountService.getMorosity(filter));
        return "pages/arrears/index";
    }
}
