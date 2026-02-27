package com.sales.maidav.web.controller.web;

import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.InvalidSaleException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class CreditAccountController {

    private final CreditAccountService creditAccountService;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String list(Model model) {
        List<CreditAccount> accounts = creditAccountService.findAll();
        Map<Long, BigDecimal> currentInstallments = new HashMap<>();
        Map<Long, String> dueSchedules = new HashMap<>();
        for (CreditAccount account : accounts) {
            BigDecimal currentAmount = creditInstallmentRepository
                    .findFirstByAccount_IdAndStatusNotOrderByInstallmentNumberAsc(
                            account.getId(),
                            InstallmentStatus.PAID
                    )
                    .map(installment -> remainingAmount(installment.getAmount(), installment.getPaidAmount()))
                    .orElse(null);
            currentInstallments.put(account.getId(), currentAmount);
            dueSchedules.put(account.getId(), formatDueSchedule(account));
        }
        model.addAttribute("accounts", accounts);
        model.addAttribute("currentInstallments", currentInstallments);
        model.addAttribute("dueSchedules", dueSchedules);
        return "pages/accounts/index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String detail(@PathVariable Long id, Model model) {
        List<CreditInstallment> installments =
                creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(id);
        BigDecimal currentInstallment = null;
        for (CreditInstallment installment : installments) {
            if (installment.getStatus() != InstallmentStatus.PAID) {
                currentInstallment = remainingAmount(installment.getAmount(), installment.getPaidAmount());
                break;
            }
        }
        model.addAttribute("account", creditAccountService.findById(id));
        model.addAttribute("installments", installments);
        model.addAttribute("currentInstallmentAmount", currentInstallment);
        model.addAttribute("payments", creditPaymentRepository.findByAccount_IdOrderByPaidAtDesc(id));
        return "pages/accounts/detail";
    }

    @GetMapping("/by-sale/{saleId}")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String detailBySale(@PathVariable Long saleId, Model model) {
        Long accountId = creditAccountService.findBySaleId(saleId).getId();
        return "redirect:/accounts/" + accountId;
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String pay(@PathVariable Long id,
                      @RequestParam BigDecimal amount,
                      @RequestParam(required = false) java.util.List<Long> installmentIds,
                      RedirectAttributes redirectAttributes) {
        try {
            creditAccountService.registerPayment(id, amount, installmentIds);
            redirectAttributes.addFlashAttribute("successMessage", "Pago registrado correctamente");
        } catch (InvalidSaleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/accounts/" + id;
    }

    private BigDecimal remainingAmount(BigDecimal amount, BigDecimal paidAmount) {
        if (amount == null) {
            return null;
        }
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal remaining = amount.subtract(paid);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    private String formatDueSchedule(CreditAccount account) {
        if (account.getPaymentFrequency() == null || account.getDueDays() == null) {
            return "-";
        }
        String[] parts = account.getDueDays().split(",");
        return switch (account.getPaymentFrequency()) {
            case DAILY -> "Diario: " + joinDaysOfWeek(parts);
            case WEEKLY -> "Semanal: " + dayOfWeekLabel(parts[0]);
            case BIWEEKLY -> "Quincenal: " + joinDaysOfMonth(parts);
            case MONTHLY -> "Mensual: " + joinDaysOfMonth(parts);
        };
    }

    private String joinDaysOfWeek(String[] parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(dayOfWeekLabel(parts[i]));
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String joinDaysOfMonth(String[] parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i] == null ? "" : parts[i].trim();
            if (value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" y ");
            }
            builder.append(value);
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String dayOfWeekLabel(String raw) {
        if (raw == null) {
            return "-";
        }
        return switch (raw.trim().toUpperCase()) {
            case "MONDAY" -> "Lunes";
            case "TUESDAY" -> "Martes";
            case "WEDNESDAY" -> "Miercoles";
            case "THURSDAY" -> "Jueves";
            case "FRIDAY" -> "Viernes";
            case "SATURDAY" -> "Sabado";
            case "SUNDAY" -> "Domingo";
            default -> raw;
        };
    }
}
