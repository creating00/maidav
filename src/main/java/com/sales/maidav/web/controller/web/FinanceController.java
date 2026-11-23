package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.finance.FinanceService;
import com.sales.maidav.service.finance.InvalidFinancePeriodException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finances")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceService financeService;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public String summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("allowedDetails", authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet()));
        model.addAttribute("paymentMethods", Map.of("CASH", "Efectivo", "BANK", "Banco", "TRANSFER", "Transferencia", "DEBIT", "Débito", "UNSPECIFIED", "Sin medio informado"));
        try {
            model.addAttribute("report", financeService.buildReport(effectiveFrom, effectiveTo, page));
        } catch (InvalidFinancePeriodException ex) {
            model.addAttribute("formError", ex.getMessage());
        }
        return "pages/finances/index";
    }
}
