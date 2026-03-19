package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.service.client.ClientService;
import com.sales.maidav.service.client.DuplicateNationalIdException;
import com.sales.maidav.service.client.InvalidNationalIdException;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.sale.InvalidSaleException;
import com.sales.maidav.service.sale.SaleItemInput;
import com.sales.maidav.service.sale.SaleService;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.settings.CompanySettingsService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final ClientService clientService;
    private final ProductService productService;
    private final UserService userService;
    private final CreditAccountService creditAccountService;
    private final CompanySettingsService companySettingsService;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CreditAccountRepository creditAccountRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_READ')")
    public String list(@RequestParam(required = false) String q, Model model) {
        List<Sale> sales = saleService.findAll();
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            sales = sales.stream()
                    .filter(s -> contains(s.getSaleNumber(), term)
                            || contains(s.getClient() != null ? s.getClient().getNationalId() : null, term)
                            || contains(s.getClient() != null ? s.getClient().getFirstName() : null, term)
                            || contains(s.getClient() != null ? s.getClient().getLastName() : null, term)
                            || contains(s.getSeller() != null ? s.getSeller().getEmail() : null, term)
                            || contains(s.getPaymentType() != null ? s.getPaymentType().name() : null, term)
                            || contains(s.getStatus() != null ? s.getStatus().name() : null, term))
                    .toList();
        }
        model.addAttribute("q", q);
        model.addAttribute("sales", sales);
        return "pages/sales/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String createForm(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("calculatorConfig", buildCalculatorConfig());
        addNumberPreviews(model);
        return "pages/sales/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.findById(id));
        model.addAttribute("items", saleItemRepository.findBySale_IdOrderByIdAsc(id));
        return "pages/sales/detail";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String create(@RequestParam(required = false) Long clientId,
                         @RequestParam PaymentType paymentType,
                         @RequestParam(required = false) LocalDate saleDate,
                         @RequestParam(required = false) LocalDate firstDueDate,
                         @RequestParam(required = false) PaymentFrequency paymentFrequency,
                         @RequestParam(required = false, name = "dailyDays") List<String> dailyDays,
                         @RequestParam(required = false) String weeklyDay,
                         @RequestParam(required = false) Integer biMonthlyDay1,
                         @RequestParam(required = false) Integer biMonthlyDay2,
                         @RequestParam(required = false) BigDecimal discountAmount,
                         @RequestParam(required = false) Integer weeksCount,
                         @RequestParam(name = "productIds") List<Long> productIds,
                         @RequestParam(name = "quantities") List<Integer> quantities,
                         @RequestParam(name = "unitPrices") List<BigDecimal> unitPrices,
                         @RequestParam(required = false) String quickClientNationalId,
                         @RequestParam(required = false) String quickClientFirstName,
                         @RequestParam(required = false) String quickClientLastName,
                         @RequestParam(required = false) String quickClientPhone,
                         @RequestParam(required = false) String quickClientAddress,
                         @RequestParam(required = false) String draftState,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        try {
            User seller = userService.findByEmail(authentication.getName());
            Client client = resolveClient(clientId, seller, quickClientNationalId, quickClientFirstName,
                    quickClientLastName, quickClientPhone, quickClientAddress);
            List<SaleItemInput> items = buildItems(productIds, quantities, unitPrices);
            List<String> dueDays = resolveDueDays(paymentFrequency, dailyDays, weeklyDay, biMonthlyDay1, biMonthlyDay2, firstDueDate);

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
        } catch (InvalidSaleException | DuplicateNationalIdException | InvalidNationalIdException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("draftState", draftState);
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("products", productService.findAll());
            model.addAttribute("calculatorConfig", buildCalculatorConfig());
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

    private Client resolveClient(Long clientId,
                                 User seller,
                                 String quickClientNationalId,
                                 String quickClientFirstName,
                                 String quickClientLastName,
                                 String quickClientPhone,
                                 String quickClientAddress) {
        if (clientId != null) {
            return clientService.findById(clientId);
        }
        if (isBlank(quickClientNationalId) || isBlank(quickClientFirstName) || isBlank(quickClientLastName)) {
            throw new InvalidSaleException("Debe seleccionar un cliente o cargar DNI, nombre y apellido");
        }
        Client client = new Client();
        client.setNationalId(quickClientNationalId.trim());
        client.setFirstName(quickClientFirstName.trim());
        client.setLastName(quickClientLastName.trim());
        client.setPhone(trimToNull(quickClientPhone));
        client.setAddress(trimToNull(quickClientAddress));
        client.setSeller(seller);
        return clientService.create(client);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                                        LocalDate firstDueDate) {
        List<String> dueDays = new ArrayList<>();
        if (paymentFrequency == null) {
            return dueDays;
        }
        switch (paymentFrequency) {
            case DAILY -> {
                if (dailyDays != null && !dailyDays.isEmpty()) {
                    dueDays.addAll(dailyDays);
                } else if (firstDueDate != null) {
                    dueDays.add(firstDueDate.getDayOfWeek().name());
                }
            }
            case WEEKLY -> {
                if (weeklyDay != null && !weeklyDay.isBlank()) {
                    dueDays.add(weeklyDay);
                } else if (firstDueDate != null) {
                    dueDays.add(firstDueDate.getDayOfWeek().name());
                }
            }
            case BIWEEKLY -> {
                if (biMonthlyDay1 != null) {
                    dueDays.add(String.valueOf(biMonthlyDay1));
                }
                if (biMonthlyDay2 != null) {
                    dueDays.add(String.valueOf(biMonthlyDay2));
                }
                if (dueDays.size() < 2) {
                    dueDays.clear();
                    dueDays.add("10");
                    dueDays.add("25");
                }
            }
            case MONTHLY -> {
                if (firstDueDate != null) {
                    dueDays.add(String.valueOf(normalizeDayOfMonth(firstDueDate.getDayOfMonth())));
                }
            }
        }
        return dueDays;
    }

    private int normalizeDayOfMonth(int day) {
        if (day < 1) {
            return 1;
        }
        return Math.min(day, 28);
    }

    private Map<String, Object> buildCalculatorConfig() {
        CompanySettings settings = companySettingsService.getSettings();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("recargo", getDecimal(settings.getCalcRecargo(), "1.26"));
        cfg.put("multContado", getDecimal(settings.getCalcMultContado(), "1.30"));
        cfg.put("multDebito", getDecimal(settings.getCalcMultDebito(), "1.50"));
        cfg.put("dias", getInt(settings.getCalcDias(), 144));
        cfg.put("intDia", getDecimal(settings.getCalcIntDia(), "2.00"));
        cfg.put("semanas", getInt(settings.getCalcSemanas(), 13));
        cfg.put("intSem", getDecimal(settings.getCalcIntSem(), "2.00"));
        cfg.put("mesesCorto", getInt(settings.getCalcMesesCorto(), 4));
        cfg.put("intMesCorto", getDecimal(settings.getCalcIntMesCorto(), "2.00"));
        cfg.put("mesesLargo", getInt(settings.getCalcMesesLargo(), 8));
        cfg.put("intMesLargo", getDecimal(settings.getCalcIntMesLargo(), "2.50"));
        return cfg;
    }

    private BigDecimal getDecimal(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    private Integer getInt(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }
}
