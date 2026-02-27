package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.user.User;
import com.sales.maidav.service.client.ClientService;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.sale.InvalidSaleException;
import com.sales.maidav.service.sale.SaleItemInput;
import com.sales.maidav.service.sale.SaleService;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.repository.sale.SaleRepository;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final ClientService clientService;
    private final ProductService productService;
    private final UserService userService;
    private final CreditAccountService creditAccountService;
    private final SaleRepository saleRepository;
    private final CreditAccountRepository creditAccountRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_READ')")
    public String list(Model model) {
        model.addAttribute("sales", saleService.findAll());
        return "pages/sales/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("products", productService.findAll());
        addNumberPreviews(model);
        return "pages/sales/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String create(@RequestParam Long clientId,
                         @RequestParam PaymentType paymentType,
                         @RequestParam(required = false) LocalDate saleDate,
                         @RequestParam(required = false) LocalDate firstDueDate,
                         @RequestParam(required = false) PaymentFrequency paymentFrequency,
                         @RequestParam(required = false, name = "dailyDays") List<String> dailyDays,
                         @RequestParam(required = false) String weeklyDay,
                         @RequestParam(required = false) Integer biMonthlyDay1,
                         @RequestParam(required = false) Integer biMonthlyDay2,
                         @RequestParam(required = false) Integer monthlyDay,
                         @RequestParam(required = false) BigDecimal discountAmount,
                         @RequestParam(required = false) Integer weeksCount,
                         @RequestParam(name = "productIds") List<Long> productIds,
                         @RequestParam(name = "quantities") List<Integer> quantities,
                         @RequestParam(name = "unitPrices") List<BigDecimal> unitPrices,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        try {
            Client client = clientService.findById(clientId);
            User seller = userService.findByEmail(authentication.getName());
            List<SaleItemInput> items = buildItems(productIds, quantities, unitPrices);
            List<String> dueDays = resolveDueDays(paymentFrequency, dailyDays, weeklyDay, biMonthlyDay1, biMonthlyDay2, monthlyDay);

            Sale sale = saleService.createSale(client, seller, paymentType, saleDate, firstDueDate, paymentFrequency, dueDays,
                    discountAmount, weeksCount, items);
            redirectAttributes.addFlashAttribute("saleNumber", sale.getSaleNumber());
            if (paymentType == PaymentType.CREDIT) {
                redirectAttributes.addFlashAttribute(
                        "accountNumber",
                        creditAccountService.findBySaleId(sale.getId()).getAccountNumber()
                );
            }
            redirectAttributes.addFlashAttribute("successMessage", "Venta guardada correctamente");
            return "redirect:/sales/new";
        } catch (InvalidSaleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("products", productService.findAll());
            addNumberPreviews(model);
            return "pages/sales/form";
        }
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('SALES_DELETE')")
    public String voidSale(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        saleService.voidSale(id);
        redirectAttributes.addFlashAttribute("successMessage", "Venta anulada correctamente");
        return "redirect:/sales";
    }

    private List<SaleItemInput> buildItems(List<Long> productIds,
                                           List<Integer> quantities,
                                           List<BigDecimal> unitPrices) {
        List<SaleItemInput> items = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            Integer qty = quantities.get(i);
            BigDecimal price = unitPrices.get(i);
            if (productId == null || qty == null || price == null) {
                continue;
            }
            items.add(new SaleItemInput(productId, qty, price));
        }
        return items;
    }

    private void addNumberPreviews(Model model) {
        if (!model.containsAttribute("saleNumber")) {
            Long nextSaleId = saleRepository.nextSaleId();
            model.addAttribute("previewSaleNumber", formatNumber("V-", nextSaleId));
        }
        if (!model.containsAttribute("accountNumber")) {
            Long nextAccountId = creditAccountRepository.nextAccountId();
            model.addAttribute("previewAccountNumber", formatNumber("C-", nextAccountId));
        }
    }

    private String formatNumber(String prefix, Long id) {
        if (id == null) {
            return null;
        }
        return prefix + String.format("%06d", id);
    }

    private List<String> resolveDueDays(PaymentFrequency paymentFrequency,
                                        List<String> dailyDays,
                                        String weeklyDay,
                                        Integer biMonthlyDay1,
                                        Integer biMonthlyDay2,
                                        Integer monthlyDay) {
        List<String> dueDays = new ArrayList<>();
        if (paymentFrequency == null) {
            return dueDays;
        }
        switch (paymentFrequency) {
            case DAILY -> {
                if (dailyDays != null) {
                    dueDays.addAll(dailyDays);
                }
            }
            case WEEKLY -> {
                if (weeklyDay != null && !weeklyDay.isBlank()) {
                    dueDays.add(weeklyDay);
                }
            }
            case BIWEEKLY -> {
                if (biMonthlyDay1 != null) {
                    dueDays.add(String.valueOf(biMonthlyDay1));
                }
                if (biMonthlyDay2 != null) {
                    dueDays.add(String.valueOf(biMonthlyDay2));
                }
            }
            case MONTHLY -> {
                if (monthlyDay != null) {
                    dueDays.add(String.valueOf(monthlyDay));
                }
            }
        }
        return dueDays;
    }
}
