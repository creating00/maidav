package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.expense.ExpenseCategory;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.expense.*;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.ExpenseForm;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;
    private final UserService userService;

    @InitBinder("expenseForm")
    void configureBinding(WebDataBinder binder) {
        binder.setAllowedFields("expenseDate", "category", "description", "amount", "paymentMethod", "operationKey");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) ExpenseCategory category,
                       @RequestParam(required = false) BigDecimal minAmount,
                       @RequestParam(required = false) BigDecimal maxAmount,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        try {
            var expenses = expenseService.findPage(from, to, category, minAmount, maxAmount, page);
            if (expenses.getTotalPages() > 0 && page >= expenses.getTotalPages()) {
                expenses = expenseService.findPage(from, to, category, minAmount, maxAmount, expenses.getTotalPages() - 1);
            }
            model.addAttribute("expenses", expenses);
        } catch (InvalidExpenseException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("expenses", Page.empty());
        }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("category", category);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        addOptions(model);
        return "pages/expenses/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public String createForm(Model model) {
        ExpenseForm form = new ExpenseForm();
        form.setExpenseDate(LocalDate.now());
        form.setOperationKey(UUID.randomUUID().toString());
        model.addAttribute("expenseForm", form);
        addOptions(model);
        return "pages/expenses/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public String create(@ModelAttribute ExpenseForm expenseForm, BindingResult errors,
                         @RequestParam(required = false) MultipartFile receipt,
                         Authentication authentication, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("formError", "Revise la fecha, la categoría, el importe y la forma de pago");
        } else {
            try {
                var user = userService.findByEmail(authentication.getName());
                var expense = expenseService.create(expenseForm, user.getId(), receipt);
                redirect.addFlashAttribute("successMessage", "Gasto registrado correctamente");
                boolean canRead = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("EXPENSE_READ"));
                return canRead ? "redirect:/expenses/" + expense.getId() : "redirect:/expenses/new";
            } catch (InvalidExpenseException ex) {
                model.addAttribute("formError", ex.getMessage());
            }
        }
        model.addAttribute("receiptMustReselect", receipt != null && !receipt.isEmpty());
        addOptions(model);
        return "pages/expenses/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("expense", expenseService.findById(id));
        addOptions(model);
        return "pages/expenses/detail";
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public ResponseEntity<ByteArrayResource> downloadReceipt(@PathVariable Long id) {
        var expense = expenseService.findById(id);
        var receipt = expenseService.findReceipt(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(expense.getReceiptName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(expense.getReceiptContentType()))
                .contentLength(receipt.getContent().length)
                .body(new ByteArrayResource(receipt.getContent()));
    }

    private void addOptions(Model model) {
        model.addAttribute("categories", ExpenseCategory.values());
        Map<PaymentCollectionMethod, String> methods = new LinkedHashMap<>();
        methods.put(PaymentCollectionMethod.CASH, "Efectivo");
        methods.put(PaymentCollectionMethod.BANK, "Banco");
        methods.put(PaymentCollectionMethod.TRANSFER, "Transferencia");
        methods.put(PaymentCollectionMethod.DEBIT, "Débito");
        model.addAttribute("paymentMethods", methods);
        model.addAttribute("today", LocalDate.now());
    }
}
