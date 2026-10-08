package com.sales.maidav.service.export;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.QuotePlanType;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditPayment;
import com.sales.maidav.model.sale.PaymentFrequency;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.quote.QuoteCalculator;
import com.sales.maidav.service.quote.QuotePlanSnapshot;
import com.sales.maidav.service.settings.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditAccountExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> HEADERS = List.of(
            "numero_cuenta", "dni_cuit", "nombre", "apellido", "telefono", "direccion",
            "fecha_alta", "fecha_cuenta", "total_credito", "saldo_total_cuenta",
            "frecuencia", "estado_cuenta", "vendedor", "cobrador", "plan_pago",
            "producto", "cantidad", "costo_producto", "precio_contado",
            "precio_contado_transferencia", "precio_transferencia", "precio_13_semanas",
            "precio_4_meses", "precio_8_meses"
    );

    private final SaleItemRepository saleItemRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final UserRepository userRepository;
    private final CompanySettingsService companySettingsService;
    private final QuoteCalculator quoteCalculator;
    private final ExportDocumentService exportDocumentService;

    @Transactional(readOnly = true)
    public byte[] export(List<CreditAccount> accounts) {
        CompanySettings settings = companySettingsService.getSettings();
        List<String> headers = headers(settings);
        if (accounts.isEmpty()) {
            return exportDocumentService.creditAccounts(headers, List.of());
        }
        List<Long> saleIds = accounts.stream().map(account -> account.getSale().getId()).toList();
        Map<Long, List<SaleItem>> items = saleItemRepository.findBySale_IdInOrderBySale_IdAscIdAsc(saleIds)
                .stream().collect(Collectors.groupingBy(item -> item.getSale().getId()));
        Map<Long, String> collectors = collectors(accounts);
        List<List<String>> rows = new ArrayList<>();
        for (CreditAccount account : accounts) {
            List<SaleItem> saleItems = items.getOrDefault(account.getSale().getId(), List.of());
            if (saleItems.isEmpty()) {
                rows.add(row(account, collectors.getOrDefault(account.getId(), ""), null, settings));
            } else {
                for (SaleItem item : saleItems) {
                    rows.add(row(account, collectors.getOrDefault(account.getId(), ""), item, settings));
                }
            }
        }
        return exportDocumentService.creditAccounts(headers, rows);
    }

    private List<String> headers(CompanySettings settings) {
        List<String> headers = new ArrayList<>(HEADERS);
        int days = count(settings.getCalcDias(), 144);
        headers.set(20, days == 144 ? "precio_transferencia" : "precio_transferencia_" + days + "_dias");
        headers.set(21, "precio_" + count(settings.getCalcSemanas(), 13) + "_semanas");
        headers.set(22, "precio_" + count(settings.getCalcMesesCorto(), 4) + "_meses");
        headers.set(23, "precio_" + count(settings.getCalcMesesLargo(), 8) + "_meses");
        return headers;
    }

    private int count(Integer value, int fallback) { return value == null || value < 1 ? fallback : value; }

    private Map<Long, String> collectors(List<CreditAccount> accounts) {
        List<Long> accountIds = accounts.stream().map(CreditAccount::getId).toList();
        List<CreditPayment> payments = creditPaymentRepository.findByAccount_IdIn(accountIds);
        Set<Long> reversedIds = payments.stream().filter(CreditPayment::isReversal)
                .map(CreditPayment::getReversalOfPaymentId)
                .filter(id -> id != null).collect(Collectors.toSet());
        List<CreditPayment> activePayments = payments.stream()
                .filter(payment -> !payment.isReversal() && !reversedIds.contains(payment.getId()))
                .filter(payment -> payment.getRegisteredBy() != null && !payment.getRegisteredBy().isBlank())
                .toList();
        List<String> emails = activePayments.stream().map(CreditPayment::getRegisteredBy)
                .map(email -> email.toLowerCase(Locale.ROOT)).distinct().toList();
        if (emails.isEmpty()) {
            return Map.of();
        }
        Set<String> eligibleEmails = userRepository.findByEmailIn(emails).stream()
                .filter(user -> user.getRoles().stream().anyMatch(role ->
                        "COBRADOR".equals(role.getName()) || "ADMIN".equals(role.getName())))
                .map(User::getEmail)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<Long, Set<String>> byAccount = new HashMap<>();
        for (CreditPayment payment : activePayments) {
            String email = payment.getRegisteredBy();
            if (eligibleEmails.contains(email.toLowerCase(Locale.ROOT))) {
                byAccount.computeIfAbsent(payment.getAccount().getId(), ignored -> new HashSet<>()).add(email);
            }
        }
        return byAccount.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().sorted().collect(Collectors.joining(", "))));
    }

    private List<String> row(CreditAccount account, String collectors, SaleItem item, CompanySettings settings) {
        Client client = account.getClient();
        Product product = item == null ? null : item.getProduct();
        List<String> row = new ArrayList<>(HEADERS.size());
        row.add(text(account.getAccountNumber()));
        row.add(client == null ? "" : text(client.getNationalId()));
        row.add(client == null ? "" : text(client.getFirstName()));
        row.add(client == null ? "" : text(client.getLastName()));
        row.add(client == null ? "" : text(client.getPhone()));
        row.add(client == null ? "" : text(client.getAddress()));
        row.add(client == null ? "" : date(client.getCreatedAt()));
        row.add(date(account.getCreatedAt()));
        row.add(amount(account.getTotalAmount()));
        row.add(amount(account.getBalance()));
        row.add(frequency(account.getPaymentFrequency()));
        row.add(account.getStatus() == null ? "" : switch (account.getStatus()) {
            case OPEN -> "Abierto";
            case CLOSED -> "Cerrado";
            case VOID -> "Anulado";
        });
        row.add(account.getSale().getSeller() == null ? "" : text(account.getSale().getSeller().getEmail()));
        row.add(collectors);
        row.add(plan(account));
        row.add(product == null ? "" : text(product.getProductCode()) + " - " + text(product.getDescription()));
        row.add(item == null ? "" : String.valueOf(item.getQuantity()));
        row.add(product == null ? "" : amount(product.getCost()));
        if (product == null) {
            row.addAll(List.of("", "", "", "", "", ""));
            return row;
        }
        BigDecimal base = value(product.getCost())
                .multiply(BigDecimal.ONE.add(value(product.getVatRate()).movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        Map<QuotePlanType, QuotePlanSnapshot> plans = quoteCalculator
                .calculatePlanSnapshots(base, value(product.getCost()), settings).stream()
                .collect(Collectors.toMap(QuotePlanSnapshot::planType, plan -> plan));
        row.add(amount(quoteCalculator.calculateCashTotal(base, settings)));
        row.add(amount(quoteCalculator.calculateDebitTotal(base, settings)));
        row.add(fee(plans, QuotePlanType.DAILY));
        row.add(fee(plans, QuotePlanType.WEEKLY));
        row.add(fee(plans, QuotePlanType.MONTHLY_SHORT));
        row.add(plans.containsKey(QuotePlanType.MONTHLY_LONG)
                ? fee(plans, QuotePlanType.MONTHLY_LONG) : "No aplica");
        return row;
    }

    private String fee(Map<QuotePlanType, QuotePlanSnapshot> plans, QuotePlanType type) {
        QuotePlanSnapshot snapshot = plans.get(type);
        return snapshot == null ? "" : amount(snapshot.feeAmount());
    }

    private String plan(CreditAccount account) {
        if (account.getPaymentFrequency() == null || account.getWeeksCount() == null) {
            return "";
        }
        String unit = switch (account.getPaymentFrequency()) {
            case DAILY -> "días";
            case WEEKLY -> "semanas";
            case BIWEEKLY -> "quincenas";
            case MONTHLY -> "meses";
        };
        return account.getWeeksCount() + " " + unit;
    }

    private String frequency(PaymentFrequency frequency) {
        if (frequency == null) return "";
        return switch (frequency) {
            case DAILY -> "Diaria";
            case WEEKLY -> "Semanal";
            case BIWEEKLY -> "Quincenal";
            case MONTHLY -> "Mensual";
        };
    }

    private String date(LocalDateTime date) { return date == null ? "" : DATE.format(date); }
    private String text(String value) { return value == null ? "" : value; }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String amount(BigDecimal value) { return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
