package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.PaymentType;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.sale.SaleStatus;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.sale.SaleRepository;
import com.sales.maidav.service.client.ClientService;
import com.sales.maidav.service.client.DuplicateNationalIdException;
import com.sales.maidav.service.client.InvalidNationalIdException;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.InvalidSaleException;
import com.sales.maidav.service.sale.SaleItemInput;
import com.sales.maidav.service.sale.SaleService;
import com.sales.maidav.service.settings.CompanySettingsService;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) PaymentType paymentType,
                       @RequestParam(required = false, defaultValue = "false") boolean showVoided,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       Model model) {
        final int pageSize = 20;
        List<Sale> sales = saleService.findAll();
        if (!showVoided) {
            sales = sales.stream()
                    .filter(sale -> sale.getStatus() != com.sales.maidav.model.sale.SaleStatus.VOID)
                    .toList();
        }
        if (paymentType != null) {
            sales = sales.stream()
                    .filter(sale -> paymentType == sale.getPaymentType())
                    .toList();
        }
        Map<Long, String> displayNumbers = buildDisplayNumbers(sales);
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            Map<Long, String> visibleNumbers = displayNumbers;
            sales = sales.stream()
                    .filter(s -> contains(visibleNumbers.get(s.getId()), term)
                            || contains(s.getSaleNumber(), term)
                            || contains(s.getClient() != null ? s.getClient().getNationalId() : null, term)
                            || contains(s.getClient() != null ? s.getClient().getFirstName() : null, term)
                            || contains(s.getClient() != null ? s.getClient().getLastName() : null, term)
                            || contains(s.getSeller() != null ? s.getSeller().getEmail() : null, term)
                            || contains(s.getPaymentType() != null ? s.getPaymentType().name() : null, term)
                            || contains(s.getStatus() != null ? s.getStatus().name() : null, term))
                    .toList();
            displayNumbers = buildDisplayNumbers(sales);
        }

        int safePage = Math.max(page, 0);
        int totalItems = sales.size();
        int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / pageSize);
        if (safePage >= totalPages) {
            safePage = totalPages - 1;
        }
        int fromIndex = Math.min(safePage * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<Sale> pagedSales = sales.subList(fromIndex, toIndex);
        displayNumbers = buildDisplayNumbers(pagedSales);

        model.addAttribute("q", q);
        model.addAttribute("paymentType", paymentType);
        model.addAttribute("showVoided", showVoided);
        model.addAttribute("sales", pagedSales);
        model.addAttribute("saleDisplayNumbers", displayNumbers);
        model.addAttribute("paymentTypes", PaymentType.values());
        model.addAttribute("page", safePage);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrevious", safePage > 0);
        model.addAttribute("hasNext", safePage + 1 < totalPages);
        return "pages/sales/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String createForm(Authentication authentication, Model model) {
        addCreateFormAttributes(model, authentication);
        return "pages/sales/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.findById(id));
        model.addAttribute("items", saleItemRepository.findBySale_IdOrderByIdAsc(id));
        return "pages/sales/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        Sale sale = saleService.findById(id);
        if (sale.getStatus() == SaleStatus.VOID) {
            model.addAttribute("formError", "No se puede editar una venta anulada");
            return "redirect:/sales/" + id;
        }
        List<SaleItem> items = saleItemRepository.findBySale_IdOrderByIdAsc(id);
        model.addAttribute("sale", sale);
        model.addAttribute("existingItems", items);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("calculatorConfig", buildCalculatorConfig());
        boolean admin = isAdmin(authentication);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("sellers", userService.findAll());
        model.addAttribute("editing", true);

        // Pre-compute form values for the template
        model.addAttribute("formSaleDate", sale.getSaleDate() != null
                ? sale.getSaleDate().toLocalDate().toString()
                : LocalDate.now().toString());
        model.addAttribute("formFirstDueDate", sale.getFirstDueDate() != null
                ? sale.getFirstDueDate().toString()
                : "");
        model.addAttribute("formDiscountAmount", sale.getDiscountAmount() != null
                ? sale.getDiscountAmount()
                : BigDecimal.ZERO);
        model.addAttribute("formPaymentType", sale.getPaymentType());
        model.addAttribute("formSellerId", sale.getSeller() != null ? sale.getSeller().getId() : null);
        model.addAttribute("formClientId", sale.getClient() != null ? sale.getClient().getId() : null);

        return "pages/sales/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String update(@PathVariable Long id,
                         @RequestParam(required = false) Long clientId,
                         @RequestParam PaymentType paymentType,
                         @RequestParam(required = false) LocalDate saleDate,
                         @RequestParam(required = false) LocalDate firstDueDate,
                         @RequestParam(required = false) PaymentFrequency paymentFrequency,
                         @RequestParam(required = false) BigDecimal discountAmount,
                         @RequestParam(required = false) Integer weeksCount,
                         @RequestParam(required = false) Long sellerId,
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
            User loggedUser = userService.findByEmail(authentication.getName());
            boolean admin = isAdmin(authentication);
            User seller = resolveSeller(loggedUser, sellerId, admin);
            Client client = resolveClient(clientId, seller, quickClientNationalId, quickClientFirstName,
                    quickClientLastName, quickClientPhone, quickClientAddress);
            List<SaleItemInput> items = buildItems(productIds, quantities, unitPrices);
            BigDecimal effectiveDiscount = admin ? discountAmount : BigDecimal.ZERO;
            List<String> dueDays = resolveDueDays(paymentFrequency, firstDueDate);

            Sale sale = saleService.updateSale(id, client, seller, paymentType, saleDate, firstDueDate,
                    paymentFrequency, dueDays, effectiveDiscount, weeksCount, items);
            redirectAttributes.addFlashAttribute("saleNumber", sale.getSaleNumber());
            if (paymentType == PaymentType.CREDIT) {
                CreditAccount account = creditAccountService.findBySaleId(sale.getId());
                redirectAttributes.addFlashAttribute("creditAccountId", account.getId());
                redirectAttributes.addFlashAttribute("accountNumber", account.getAccountNumber());
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Venta actualizada correctamente. ID de credito: " + account.getId() +
                                (account.getAccountNumber() != null ? " | Numero de cuenta: " + account.getAccountNumber() : "")
                );
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Venta actualizada correctamente");
            }
            return "redirect:/sales/" + id;
        } catch (InvalidSaleException | DuplicateNationalIdException | InvalidNationalIdException ex) {
            Sale failedSale = saleService.findById(id);
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("draftState", draftState);
            model.addAttribute("sale", failedSale);
            model.addAttribute("existingItems", saleItemRepository.findBySale_IdOrderByIdAsc(id));
            model.addAttribute("editing", true);
            addCreateFormAttributes(model, authentication);
            return "pages/sales/form";
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public String create(@RequestParam(required = false) Long clientId,
                         @RequestParam PaymentType paymentType,
                         @RequestParam(required = false) LocalDate saleDate,
                         @RequestParam(required = false) LocalDate firstDueDate,
                         @RequestParam(required = false) PaymentFrequency paymentFrequency,
                         @RequestParam(required = false) BigDecimal discountAmount,
                         @RequestParam(required = false) Integer weeksCount,
                         @RequestParam(required = false) Long sellerId,
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
            User loggedUser = userService.findByEmail(authentication.getName());
            boolean admin = isAdmin(authentication);
            // SOLO ADMIN ASIGNAR VENDEDOR
            User seller = resolveSeller(loggedUser, sellerId, admin);
            Client client = resolveClient(clientId, seller, quickClientNationalId, quickClientFirstName,
                    quickClientLastName, quickClientPhone, quickClientAddress);
            List<SaleItemInput> items = buildItems(productIds, quantities, unitPrices);
            // FIX FECHA VENCIMIENTO
            // REMOVER CONFLICTO FRECUENCIA
            List<String> dueDays = resolveDueDays(paymentFrequency, firstDueDate);
            // SOLO ADMIN DESCUENTO
            BigDecimal effectiveDiscount = admin ? discountAmount : BigDecimal.ZERO;

            Sale sale = saleService.createSale(client, seller, paymentType, saleDate, firstDueDate, paymentFrequency, dueDays,
                    effectiveDiscount, weeksCount, items);
            redirectAttributes.addFlashAttribute("saleNumber", sale.getSaleNumber());
            if (paymentType == PaymentType.CREDIT) {
                CreditAccount account = creditAccountService.findBySaleId(sale.getId());
                redirectAttributes.addFlashAttribute("creditAccountId", account.getId());
                redirectAttributes.addFlashAttribute("accountNumber", account.getAccountNumber());
                // MOSTRAR ID CREDITO
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Venta registrada correctamente. ID de credito: " + account.getId() +
                                (account.getAccountNumber() != null ? " | Numero de cuenta: " + account.getAccountNumber() : "")
                );
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Venta registrada correctamente");
            }
            return "redirect:/sales/new";
        } catch (InvalidSaleException | DuplicateNationalIdException | InvalidNationalIdException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("draftState", draftState);
            addCreateFormAttributes(model, authentication);
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

    private void addCreateFormAttributes(Model model, Authentication authentication) {
        if (!model.containsAttribute("editing")) {
            model.addAttribute("editing", false);
        }
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("calculatorConfig", buildCalculatorConfig());
        boolean admin = isAdmin(authentication);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("sellers", userService.findAll());
        addNumberPreviews(model);
    }

    private User resolveSeller(User loggedUser, Long sellerId, boolean admin) {
        if (!admin || sellerId == null) {
            return loggedUser;
        }
        return userService.findById(sellerId);
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

    private Map<Long, String> buildDisplayNumbers(List<Sale> sales) {
        Map<Long, String> displayNumbers = new HashMap<>();
        if (sales == null || sales.isEmpty()) {
            return displayNumbers;
        }

        List<Long> creditSaleIds = sales.stream()
                .filter(sale -> sale.getId() != null && sale.getPaymentType() == PaymentType.CREDIT)
                .map(Sale::getId)
                .toList();
        Map<Long, String> accountNumberBySaleId = new HashMap<>();
        if (!creditSaleIds.isEmpty()) {
            for (CreditAccount account : creditAccountRepository.findBySale_IdIn(creditSaleIds)) {
                if (account.getSale() != null && account.getSale().getId() != null) {
                    accountNumberBySaleId.put(account.getSale().getId(), account.getAccountNumber());
                }
            }
        }

        for (Sale sale : sales) {
            if (sale.getId() == null) {
                continue;
            }
            String displayNumber = sale.getPaymentType() == PaymentType.CREDIT
                    ? accountNumberBySaleId.get(sale.getId())
                    : sale.getSaleNumber();
            if (displayNumber == null || displayNumber.isBlank()) {
                displayNumber = sale.getSaleNumber();
            }
            if (displayNumber == null || displayNumber.isBlank()) {
                displayNumber = String.valueOf(sale.getId());
            }
            displayNumbers.put(sale.getId(), displayNumber);
        }
        return displayNumbers;
    }

    private String formatNumber(String prefix, Long id) {
        if (id == null) {
            return null;
        }
        return prefix + String.format("%06d", id);
    }

    private List<String> resolveDueDays(PaymentFrequency paymentFrequency,
                                        LocalDate firstDueDate) {
        List<String> dueDays = new ArrayList<>();
        if (paymentFrequency == null || firstDueDate == null) {
            return dueDays;
        }
        switch (paymentFrequency) {
            case DAILY, WEEKLY -> dueDays.add(firstDueDate.getDayOfWeek().name());
            case BIWEEKLY, MONTHLY -> dueDays.add(String.valueOf(normalizeDayOfMonth(firstDueDate.getDayOfMonth())));
        }
        return dueDays;
    }

    private int normalizeDayOfMonth(int day) {
        if (day < 1) {
            return 1;
        }
        return Math.min(day, 28);
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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

