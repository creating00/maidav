package com.sales.maidav.web.controller.web;

import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.service.sale.CreditPaymentPricingSupport;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.InvalidSaleException;
import com.sales.maidav.service.settings.CompanySettingsService;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class CreditAccountController {

    private final CreditAccountService creditAccountService;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final CompanySettingsService companySettingsService;
    private final SaleItemRepository saleItemRepository;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long sellerId,
                       Authentication authentication,
                       Model model) {
        List<CreditAccount> accounts = creditAccountService.findAll();
        Long effectiveSellerId = resolveSellerFilter(authentication, sellerId);
        if (effectiveSellerId != null) {
            // FILTRO POR VENDEDOR
            // FILTRO VENDEDOR BACKEND
            accounts = accounts.stream()
                    .filter(account -> account.getSale() != null
                            && account.getSale().getSeller() != null
                            && effectiveSellerId.equals(account.getSale().getSeller().getId()))
                    .toList();
        }
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            accounts = accounts.stream()
                    .filter(a -> contains(String.valueOf(a.getId()), term)
                            || contains(a.getAccountNumber(), term)
                            || contains(a.getClient() != null ? a.getClient().getNationalId() : null, term)
                            || contains(a.getClient() != null ? a.getClient().getFirstName() : null, term)
                            || contains(a.getClient() != null ? a.getClient().getLastName() : null, term)
                            || contains(a.getStatus() != null ? a.getStatus().name() : null, term))
                    .toList();
        }
        Map<Long, BigDecimal> currentInstallments = new HashMap<>();
        Map<Long, String> dueSchedules = new HashMap<>();
        for (CreditAccount account : accounts) {
            BigDecimal currentAmount = creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId()).stream()
                    .filter(installment -> installment.getStatus() != InstallmentStatus.PAID)
                    .filter(installment -> installment.getStatus() != InstallmentStatus.VOID)
                    .filter(installment -> !installment.isVoided())
                    .findFirst()
                    .map(installment -> chargeAmount(account, installment))
                    .orElse(null);
            currentInstallments.put(account.getId(), currentAmount);
            dueSchedules.put(account.getId(), formatDueSchedule(account));
        }
        Map<Long, List<AccountProductItemView>> productsByAccount = buildProductsByAccount(accounts);
        model.addAttribute("q", q);
        model.addAttribute("sellerId", effectiveSellerId);
        // FILTRO VENDEDOR FRONTEND
        model.addAttribute("sellerOptions", sellerOptions(authentication));
        model.addAttribute("accounts", accounts);
        model.addAttribute("currentInstallments", currentInstallments);
        model.addAttribute("dueSchedules", dueSchedules);
        model.addAttribute("clientGroups", buildClientGroups(accounts, currentInstallments, dueSchedules, productsByAccount));
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "pages/accounts/index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String detail(@PathVariable Long id, Model model) {
        CreditAccount account = creditAccountService.findById(id);
        List<CreditInstallment> installments =
                creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(id);
        List<CreditInstallment> activeInstallments = installments.stream()
                .filter(installment -> installment.getStatus() != InstallmentStatus.VOID)
                .filter(installment -> !installment.isVoided())
                .toList();
        List<CreditInstallment> voidedInstallments = installments.stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.VOID || installment.isVoided())
                .toList();
        BigDecimal currentInstallment = null;
        Integer currentInstallmentNumber = null;
        BigDecimal currentInstallmentBaseAmount = null;
        BigDecimal currentInstallmentAppliedCredit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentInstallmentExpiredCash = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Boolean currentInstallmentCashPricing = null;
        Map<Long, BigDecimal> chargeAmounts = new HashMap<>();
        Map<Long, BigDecimal> baseChargeAmounts = new HashMap<>();
        Map<Long, BigDecimal> financedAmounts = new HashMap<>();
        Map<Long, Boolean> cashPricingAvailable = new HashMap<>();
        Map<Long, BigDecimal> appliedCreditAmounts = new HashMap<>();
        Map<Long, BigDecimal> expiredCashAmounts = new HashMap<>();
        BigDecimal cashRecargo = resolveCashRecargo();
        for (CreditInstallment installment : activeInstallments) {
            BigDecimal remaining = remainingAmount(installment.getAmount(), installment.getPaidAmount());
            BigDecimal chargeAmount = resolveChargeAmount(account, installment, remaining, LocalDate.now(), cashRecargo);
            BigDecimal fullCashAmount = resolveFullCashAmount(account, installment, cashRecargo);
            chargeAmounts.put(installment.getId(), chargeAmount);
            financedAmounts.put(installment.getId(), remaining);
            boolean usesCashValue = CreditPaymentPricingSupport.usesCashValue(
                    account.getPaymentFrequency(),
                    installment.getDueDate(),
                    LocalDate.now()
            );
            cashPricingAvailable.put(installment.getId(), usesCashValue);
            BigDecimal appliedCreditAmount = usesCashValue
                    ? normalizeAmount(fullCashAmount.subtract(chargeAmount))
                    : normalizeAmount(installment.getAmount().subtract(remaining));
            if (appliedCreditAmount.compareTo(BigDecimal.ZERO) < 0) {
                appliedCreditAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal baseChargeAmount = normalizeAmount(chargeAmount.add(appliedCreditAmount));
            baseChargeAmounts.put(installment.getId(), baseChargeAmount);
            appliedCreditAmounts.put(installment.getId(), appliedCreditAmount);
            BigDecimal expiredCashAmount = !usesCashValue && fullCashAmount.compareTo(BigDecimal.ZERO) > 0
                    ? fullCashAmount
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            expiredCashAmounts.put(installment.getId(), expiredCashAmount);
            if (currentInstallment == null
                    && installment.getStatus() != InstallmentStatus.PAID
                    && installment.getStatus() != InstallmentStatus.VOID
                    && !installment.isVoided()) {
                currentInstallment = chargeAmount;
                currentInstallmentNumber = installment.getInstallmentNumber();
                currentInstallmentBaseAmount = baseChargeAmount;
                currentInstallmentAppliedCredit = appliedCreditAmount;
                currentInstallmentExpiredCash = expiredCashAmount;
                currentInstallmentCashPricing = usesCashValue;
            }
        }
        model.addAttribute("account", account);
        model.addAttribute("installments", installments);
        model.addAttribute("activeInstallments", activeInstallments);
        model.addAttribute("voidedInstallments", voidedInstallments);
        model.addAttribute("currentInstallmentAmount", currentInstallment);
        model.addAttribute("currentInstallmentNumber", currentInstallmentNumber);
        model.addAttribute("currentInstallmentBaseAmount", currentInstallmentBaseAmount);
        model.addAttribute("currentInstallmentAppliedCredit", currentInstallmentAppliedCredit);
        model.addAttribute("currentInstallmentExpiredCash", currentInstallmentExpiredCash);
        model.addAttribute("currentInstallmentCashPricing", currentInstallmentCashPricing);
        model.addAttribute("paymentFrequencyLabel", paymentFrequencyLabel(account));
        model.addAttribute("chargeAmounts", chargeAmounts);
        model.addAttribute("baseChargeAmounts", baseChargeAmounts);
        model.addAttribute("financedAmounts", financedAmounts);
        model.addAttribute("cashPricingAvailable", cashPricingAvailable);
        model.addAttribute("appliedCreditAmounts", appliedCreditAmounts);
        model.addAttribute("expiredCashAmounts", expiredCashAmounts);
        model.addAttribute("payments", creditPaymentRepository.findByAccount_IdOrderByPaidAtDescIdDesc(id));
        model.addAttribute("paymentMethods", PaymentCollectionMethod.values());
        model.addAttribute("cashRecargo", cashRecargo);
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
                      @RequestParam(required = false, defaultValue = "BANK") PaymentCollectionMethod paymentMethod,
                      @RequestParam(required = false) String operationToken,
                      Authentication authentication,
                      RedirectAttributes redirectAttributes) {
        try {
            String registeredBy = authentication != null ? authentication.getName() : null;
            // VALIDACION BACKEND PAGO
            creditAccountService.registerPayment(id, amount, installmentIds, registeredBy, paymentMethod, operationToken);
            redirectAttributes.addFlashAttribute("successMessage", "Pago registrado correctamente");
        } catch (InvalidSaleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/accounts/" + id;
    }

    @PostMapping("/bulk-pay")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String bulkPay(@RequestParam String entries,
                          @RequestParam(required = false, defaultValue = "BANK") PaymentCollectionMethod paymentMethod,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        List<String> successEntries = new ArrayList<>();
        List<String> errorEntries = new ArrayList<>();
        String registeredBy = authentication != null ? authentication.getName() : null;

        int lineNumber = 0;
        for (String rawLine : entries.split("\\r?\\n")) {
            lineNumber++;
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                BulkPaymentEntry entry = parseBulkPaymentEntry(line);
                creditAccountService.registerPayment(
                        entry.accountId(),
                        entry.amount(),
                        null,
                        registeredBy,
                        paymentMethod
                );
                successEntries.add("Credito " + entry.accountId() + ": $" + entry.amount());
            } catch (RuntimeException ex) {
                if ("Encabezado o fila vacia".equals(ex.getMessage())) {
                    continue;
                }
                errorEntries.add("Línea " + lineNumber + " (" + line + "): " + ex.getMessage());
            }
        }

        if (!successEntries.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    successEntries.size() + " pago(s) impactado(s): " + String.join(" | ", successEntries)
            );
        }
        if (!errorEntries.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", String.join(" | ", errorEntries));
        }
        if (successEntries.isEmpty() && errorEntries.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Pegá al menos una línea con el formato: ID_CREDITO TAB/COMA/; MONTO"
            );
        }
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/payments/{paymentId}/update")
    @PreAuthorize("hasAuthority('ARREARS_UPDATE_PAYMENT')")
    public String updatePayment(@PathVariable Long id,
                                @PathVariable Long paymentId,
                                @RequestParam BigDecimal amount,
                                @RequestParam LocalDate paidAt,
                                @RequestParam(required = false, defaultValue = "BANK") PaymentCollectionMethod paymentMethod,
                                RedirectAttributes redirectAttributes) {
        try {
            creditAccountService.updatePayment(id, paymentId, amount, paidAt, paymentMethod);
            redirectAttributes.addFlashAttribute("successMessage", "Pago actualizado correctamente");
        } catch (InvalidSaleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/accounts/" + id;
    }

    @PostMapping("/{id}/installments/{installmentId}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public String voidInstallment(@PathVariable Long id,
                                  @PathVariable Long installmentId,
                                  @RequestParam(required = false) String reason,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            String voidedBy = authentication != null ? authentication.getName() : null;
            // SOLO ADMIN PUEDE ANULAR
            // ANULACION DE CUOTA
            // AUDITORIA ANULACION
            creditAccountService.voidInstallment(id, installmentId, voidedBy, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Cuota anulada correctamente");
        } catch (InvalidSaleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/accounts/" + id;
    }

    @PostMapping("/{id}/close-day")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String closeDay(@PathVariable Long id,
                           RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMessage", "Rendicion procesada y cierre diario registrado");
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
            case DAILY -> "Diario (domingo excluido)";
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

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private List<ClientGroupView> buildClientGroups(List<CreditAccount> accounts,
                                                    Map<Long, BigDecimal> currentInstallments,
                                                    Map<Long, String> dueSchedules,
                                                    Map<Long, List<AccountProductItemView>> productsByAccount) {
        Map<Long, MutableClientGroup> groups = new LinkedHashMap<>();
        List<CreditAccount> sortedAccounts = accounts.stream()
                .sorted((a, b) -> {
                    String aName = (a.getClient().getLastName() + " " + a.getClient().getFirstName()).trim();
                    String bName = (b.getClient().getLastName() + " " + b.getClient().getFirstName()).trim();
                    return aName.compareToIgnoreCase(bName);
                })
                .toList();

        for (CreditAccount account : sortedAccounts) {
            AccountBadge badge = resolveAccountBadge(account.getId());
            MutableClientGroup group = groups.computeIfAbsent(
                    account.getClient().getId(),
                    ignored -> new MutableClientGroup(
                            account.getClient().getFirstName() + " " + account.getClient().getLastName(),
                            account.getClient().getNationalId()
                    )
            );

            group.totalBalance = group.totalBalance.add(account.getBalance());
            if (account.getStatus() != AccountStatus.CLOSED) {
                group.activeCredits++;
            }
            if (badge.priority > group.badge.priority) {
                group.badge = badge;
            }
            group.accounts.add(new ClientAccountItemView(
                    account.getId(),
                    account.getAccountNumber(),
                    account.getBalance(),
                    currentInstallments.get(account.getId()),
                    paymentFrequencyLabel(account),
                    dueSchedules.get(account.getId()),
                    badge.label,
                    badge.cssClass,
                    resolveSellerDisplay(account),
                    productsByAccount.getOrDefault(account.getId(), List.of())
            ));
        }

        return groups.values().stream()
                .map(group -> new ClientGroupView(
                        group.clientName,
                        group.nationalId,
                        group.totalBalance,
                        group.activeCredits,
                        group.badge.label,
                        group.badge.cssClass,
                        group.accounts
                ))
                .toList();
    }

    private Map<Long, List<AccountProductItemView>> buildProductsByAccount(List<CreditAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> saleIdToAccountId = new LinkedHashMap<>();
        for (CreditAccount account : accounts) {
            if (account.getSale() == null || account.getSale().getId() == null) {
                continue;
            }
            saleIdToAccountId.put(account.getSale().getId(), account.getId());
        }
        if (saleIdToAccountId.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<AccountProductItemView>> productsByAccount = new LinkedHashMap<>();
        List<SaleItem> saleItems = saleItemRepository.findBySale_IdInOrderBySale_IdAscIdAsc(new ArrayList<>(saleIdToAccountId.keySet()));
        for (SaleItem saleItem : saleItems) {
            if (saleItem.getSale() == null || saleItem.getSale().getId() == null) {
                continue;
            }
            Long accountId = saleIdToAccountId.get(saleItem.getSale().getId());
            if (accountId == null) {
                continue;
            }
            productsByAccount.computeIfAbsent(accountId, ignored -> new ArrayList<>())
                    .add(new AccountProductItemView(
                            saleItem.getProduct() != null ? saleItem.getProduct().getProductCode() : null,
                            saleItem.getProduct() != null ? saleItem.getProduct().getDescription() : null,
                            saleItem.getQuantity(),
                            saleItem.getLineTotal()
                    ));
        }
        return productsByAccount;
    }

    private AccountBadge resolveAccountBadge(Long accountId) {
        List<CreditInstallment> installments = creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(accountId);
        LocalDate today = LocalDate.now();
        long overdueDays = 0;
        long dueSoonDays = Long.MAX_VALUE;

        for (CreditInstallment installment : installments) {
            if (installment.getStatus() == InstallmentStatus.PAID || installment.getStatus() == InstallmentStatus.VOID || installment.isVoided() || installment.getDueDate() == null) {
                continue;
            }
            if (installment.getDueDate().isBefore(today)) {
                overdueDays = Math.max(overdueDays, java.time.temporal.ChronoUnit.DAYS.between(installment.getDueDate(), today));
            } else {
                long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, installment.getDueDate());
                dueSoonDays = Math.min(dueSoonDays, daysUntilDue);
            }
        }

        if (overdueDays > 0) {
            return new AccountBadge("En mora · " + overdueDays + " dia(s)", "text-red-600", 3);
        }
        if (dueSoonDays != Long.MAX_VALUE && dueSoonDays <= 7) {
            return new AccountBadge("Pronto a vencer · " + dueSoonDays + " dia(s)", "text-amber-600", 2);
        }
        return new AccountBadge("Al dia", "text-emerald-600", 1);
    }

    private String paymentFrequencyLabel(CreditAccount account) {
        if (account == null || account.getPaymentFrequency() == null) {
            return "-";
        }
        return switch (account.getPaymentFrequency()) {
            case DAILY -> "Diaria";
            case WEEKLY -> "Semanal";
            case BIWEEKLY -> "Quincenal";
            case MONTHLY -> "Mensual";
        };
    }

    private String resolveSellerDisplay(CreditAccount account) {
        if (account == null || account.getSale() == null || account.getSale().getSeller() == null) {
            return "-";
        }
        String firstName = account.getSale().getSeller().getFirstName();
        String lastName = account.getSale().getSeller().getLastName();
        String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        String email = account.getSale().getSeller().getEmail();
        return email == null || email.isBlank() ? "-" : email;
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Long resolveSellerFilter(Authentication authentication, Long sellerId) {
        if (isAdmin(authentication)) {
            return sellerId == null || sellerId <= 0 ? null : sellerId;
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userService.findByEmail(authentication.getName()).getId();
    }

    private List<SellerOption> sellerOptions(Authentication authentication) {
        if (isAdmin(authentication)) {
            return userService.findByRoleName("VENDEDOR").stream()
                    .map(user -> new SellerOption(user.getId(), sellerLabel(user)))
                    .toList();
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return List.of();
        }
        var currentUser = userService.findByEmail(authentication.getName());
        return List.of(new SellerOption(currentUser.getId(), sellerLabel(currentUser)));
    }

    private String sellerLabel(com.sales.maidav.model.user.User user) {
        if (user == null) {
            return "-";
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private BulkPaymentEntry parseBulkPaymentEntry(String line) {
        List<String> parts = splitBulkPaymentLine(line);
        if (parts.isEmpty() || isHeaderRow(parts)) {
            throw new IllegalArgumentException("Encabezado o fila vacia");
        }

        int accountIndex = findAccountIndex(parts);
        int amountIndex = findAmountIndex(parts, accountIndex);
        if (accountIndex < 0 || amountIndex < 0) {
            throw new IllegalArgumentException("Formato invalido. Use: ID_CREDITO y MONTO");
        }

        Long accountId;
        try {
            accountId = Long.parseLong(parts.get(accountIndex).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El ID de credito debe ser numerico");
        }

        String rawAmount = parts.get(amountIndex).trim().replace(" ", "");
        if (rawAmount.contains(",") && rawAmount.contains(".")) {
            rawAmount = rawAmount.replace(".", "").replace(",", ".");
        } else if (rawAmount.contains(",")) {
            rawAmount = rawAmount.replace(",", ".");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El monto no es valido");
        }

        return new BulkPaymentEntry(accountId, amount);
    }

    private List<String> splitBulkPaymentLine(String line) {
        if (line.contains("\t")) {
            List<String> parts = new ArrayList<>();
            for (String part : line.split("\\t", -1)) {
                parts.add(part == null ? "" : part.trim());
            }
            return parts;
        }
        if (line.contains(";")) {
            String[] raw = line.split(";+", 2);
            return List.of(raw[0].trim(), raw.length > 1 ? raw[1].trim() : "");
        }
        if (line.matches("^\\S+\\s{2,}.+$")) {
            String[] raw = line.split("\\s{2,}", 2);
            return List.of(raw[0].trim(), raw.length > 1 ? raw[1].trim() : "");
        }
        if (line.contains(",")) {
            String[] raw = line.split(",", 2);
            return List.of(raw[0].trim(), raw.length > 1 ? raw[1].trim() : "");
        }
        return List.of(line.trim());
    }

    private boolean isHeaderRow(List<String> parts) {
        String normalized = String.join(" ", parts).toLowerCase(Locale.ROOT);
        return normalized.contains("credito") && normalized.contains("pago");
    }

    private int findAccountIndex(List<String> parts) {
        for (int i = 0; i < Math.min(parts.size(), 3); i++) {
            if (isInteger(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findAmountIndex(List<String> parts, int accountIndex) {
        for (int i = accountIndex + 1; i < parts.size(); i++) {
            if (isDecimal(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInteger(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.matches("^\\d+$");
    }

    private boolean isDecimal(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().replace(" ", "");
        return !trimmed.isEmpty() && trimmed.matches("^\\d+(?:[\\.,]\\d+)?$");
    }

    private BigDecimal resolveCashRecargo() {
        CompanySettings settings = companySettingsService.getSettings();
        BigDecimal recargo = settings.getCalcRecargo();
        if (recargo == null || recargo.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1.26");
        }
        return recargo;
    }

    private BigDecimal chargeAmount(CreditAccount account, CreditInstallment installment) {
        return resolveChargeAmount(
                account,
                installment,
                remainingAmount(installment.getAmount(), installment.getPaidAmount()),
                LocalDate.now(),
                resolveCashRecargo()
        );
    }

    private BigDecimal resolveChargeAmount(CreditAccount account,
                                           CreditInstallment installment,
                                           BigDecimal remaining,
                                           LocalDate paymentDate,
                                           BigDecimal cashRecargo) {
        return CreditPaymentPricingSupport.resolveCollectedAmountDue(
                remaining,
                cashRecargo,
                account.getPaymentFrequency(),
                installment.getDueDate(),
                paymentDate
        );
    }

    private BigDecimal resolveFullCashAmount(CreditAccount account,
                                             CreditInstallment installment,
                                             BigDecimal cashRecargo) {
        return CreditPaymentPricingSupport.resolveInstallmentCashValue(
                installment.getAmount(),
                cashRecargo,
                account.getPaymentFrequency()
        );
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static class MutableClientGroup {
        private final String clientName;
        private final String nationalId;
        private BigDecimal totalBalance = BigDecimal.ZERO;
        private int activeCredits = 0;
        private AccountBadge badge = new AccountBadge("Al dia", "text-emerald-600", 1);
        private final List<ClientAccountItemView> accounts = new ArrayList<>();

        private MutableClientGroup(String clientName, String nationalId) {
            this.clientName = clientName;
            this.nationalId = nationalId;
        }
    }

    private record AccountBadge(String label, String cssClass, int priority) {}
    private record ClientAccountItemView(Long id, String accountNumber, BigDecimal balance, BigDecimal currentInstallment,
                                         String paymentFrequency,
                                         String dueSchedule, String badgeLabel, String badgeClass,
                                         String sellerDisplay,
                                         List<AccountProductItemView> products) {}
    private record AccountProductItemView(String productCode, String description, Integer quantity, BigDecimal lineTotal) {}
    private record ClientGroupView(String clientName, String nationalId, BigDecimal totalBalance, int activeCredits,
                                   String badgeLabel, String badgeClass, List<ClientAccountItemView> accounts) {}
    private record BulkPaymentEntry(Long accountId, BigDecimal amount) {}
    private record SellerOption(Long id, String label) {}
}

