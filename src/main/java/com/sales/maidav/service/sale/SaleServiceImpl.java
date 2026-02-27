package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.*;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.sale.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;

    @Override
    public List<Sale> findAll() {
        return saleRepository.findAll();
    }

    @Override
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }

    @Override
    public Sale createSale(Client client,
                           User seller,
                           PaymentType paymentType,
                           LocalDate saleDate,
                           LocalDate firstDueDate,
                           PaymentFrequency paymentFrequency,
                           List<String> dueDays,
                           BigDecimal discountAmount,
                           Integer weeksCount,
                           List<SaleItemInput> items) {

        if (client == null) {
            throw new InvalidSaleException("Debe seleccionar un cliente");
        }
        if (seller == null) {
            throw new InvalidSaleException("Debe asignar un vendedor");
        }
        if (paymentType == null) {
            throw new InvalidSaleException("Debe seleccionar la forma de pago");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidSaleException("Debe agregar al menos un producto");
        }
        if (paymentType == PaymentType.CREDIT) {
            if (firstDueDate == null) {
                throw new InvalidSaleException("Debe seleccionar la fecha de primer vencimiento");
            }
            if (paymentFrequency == null) {
                throw new InvalidSaleException("Debe seleccionar la frecuencia de pago");
            }
            if (dueDays == null || dueDays.isEmpty()) {
                throw new InvalidSaleException("Debe seleccionar el dia de vencimiento");
            }
        }

        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El descuento no puede ser negativo");
        }

        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (SaleItemInput input : items) {
            if (input.getProductId() == null || input.getQuantity() == null || input.getUnitPrice() == null) {
                throw new InvalidSaleException("Producto, cantidad y precio son obligatorios");
            }
            if (input.getQuantity() <= 0) {
                throw new InvalidSaleException("Cantidad invalida");
            }

            Product product = productRepository.findById(input.getProductId())
                    .orElseThrow(() -> new InvalidSaleException("Producto no encontrado"));

            if (product.getStockAvailable() < input.getQuantity()) {
                throw new InvalidSaleException("Stock insuficiente para " + product.getDescription());
            }

            BigDecimal lineTotal = input.getUnitPrice()
                    .multiply(BigDecimal.valueOf(input.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(input.getQuantity());
            item.setUnitPrice(input.getUnitPrice());
            item.setLineTotal(lineTotal);
            saleItems.add(item);
            total = total.add(lineTotal);
        }

        total = total.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El total no puede ser negativo");
        }

        Sale sale = new Sale();
        sale.setClient(client);
        sale.setSeller(seller);
        sale.setPaymentType(paymentType);
        sale.setStatus(SaleStatus.ACTIVE);
        sale.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        sale.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        sale.setSaleDate(saleDate == null ? LocalDateTime.now() : saleDate.atStartOfDay());
        sale.setFirstDueDate(firstDueDate);

        sale = saleRepository.save(sale);
        if (sale.getSaleNumber() == null || sale.getSaleNumber().isBlank()) {
            sale.setSaleNumber(formatNumber("V-", sale.getId()));
            saleRepository.save(sale);
        }

        for (SaleItem item : saleItems) {
            item.setSale(sale);
            saleItemRepository.save(item);
            Product product = item.getProduct();
            product.setStockAvailable(product.getStockAvailable() - item.getQuantity());
            productRepository.save(product);
        }

        if (paymentType == PaymentType.CREDIT) {
            createAccountSchedule(sale, paymentFrequency, dueDays, firstDueDate, weeksCount);
        }

        return sale;
    }

    @Override
    public void voidSale(Long id) {
        Sale sale = findById(id);
        if (sale.getStatus() == SaleStatus.VOID) {
            return;
        }
        sale.setStatus(SaleStatus.VOID);

        List<SaleItem> items = saleItemRepository.findBySale_Id(id);
        for (SaleItem item : items) {
            Product product = item.getProduct();
            product.setStockAvailable(product.getStockAvailable() + item.getQuantity());
            productRepository.save(product);
        }
    }

    @Override
    public long countActive() {
        return saleRepository.countByStatus(SaleStatus.ACTIVE);
    }

    @Override
    public long countToday() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return saleRepository.countBySaleDateBetweenAndStatus(start, end, SaleStatus.ACTIVE);
    }

    private void createAccountSchedule(Sale sale,
                                       PaymentFrequency paymentFrequency,
                                       List<String> dueDays,
                                       LocalDate firstDueDate,
                                       Integer weeksCount) {
        if (weeksCount == null || weeksCount < 1 || weeksCount > 12) {
            throw new InvalidSaleException("Cantidad de cuotas invalida (1-12)");
        }
        if (firstDueDate == null) {
            throw new InvalidSaleException("Fecha de primer vencimiento invalida");
        }

        CreditAccount account = new CreditAccount();
        account.setSale(sale);
        account.setClient(sale.getClient());
        account.setTotalAmount(sale.getTotalAmount());
        account.setBalance(sale.getTotalAmount());
        account.setWeeksCount(weeksCount);
        account.setDueDay(resolvePrimaryDueDay(paymentFrequency, dueDays));
        account.setPaymentFrequency(paymentFrequency);
        account.setDueDays(String.join(",", dueDays));
        account.setStartDate(firstDueDate);
        account.setStatus(AccountStatus.OPEN);
        account = creditAccountRepository.save(account);
        if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
            account.setAccountNumber(formatNumber("C-", account.getId()));
            creditAccountRepository.save(account);
        }

        BigDecimal weekly = sale.getTotalAmount()
                .divide(BigDecimal.valueOf(weeksCount), 2, RoundingMode.HALF_UP);
        BigDecimal accumulated = BigDecimal.ZERO;

        List<LocalDate> schedule = buildSchedule(paymentFrequency, dueDays, firstDueDate, weeksCount);

        for (int i = 1; i <= weeksCount; i++) {
            BigDecimal amount = weekly;
            if (i == weeksCount) {
                amount = sale.getTotalAmount().subtract(accumulated);
            }
            accumulated = accumulated.add(amount);

            CreditInstallment installment = new CreditInstallment();
            installment.setAccount(account);
            installment.setInstallmentNumber(i);
            installment.setDueDate(schedule.get(i - 1));
            installment.setAmount(amount);
            installment.setPaidAmount(BigDecimal.ZERO);
            installment.setStatus(InstallmentStatus.PENDING);
            creditInstallmentRepository.save(installment);
        }
    }

    private Integer resolvePrimaryDueDay(PaymentFrequency frequency, List<String> dueDays) {
        if (frequency == PaymentFrequency.BIWEEKLY || frequency == PaymentFrequency.MONTHLY) {
            int day = parseDayOfMonth(dueDays.get(0));
            return day;
        }
        return null;
    }

    private List<LocalDate> buildSchedule(PaymentFrequency frequency,
                                          List<String> dueDays,
                                          LocalDate firstDueDate,
                                          int count) {
        if (count < 1) {
            throw new InvalidSaleException("Cantidad de cuotas invalida");
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = firstDueDate;
        switch (frequency) {
            case DAILY -> {
                Set<DayOfWeek> days = parseDaysOfWeek(dueDays);
                if (!days.contains(firstDueDate.getDayOfWeek())) {
                    throw new InvalidSaleException("La fecha de primer vencimiento no coincide con los dias seleccionados");
                }
                while (dates.size() < count) {
                    if (days.contains(cursor.getDayOfWeek())) {
                        dates.add(cursor);
                    }
                    cursor = cursor.plusDays(1);
                }
            }
            case WEEKLY -> {
                DayOfWeek weeklyDay = parseDayOfWeek(dueDays.get(0));
                if (weeklyDay != firstDueDate.getDayOfWeek()) {
                    throw new InvalidSaleException("La fecha de primer vencimiento no coincide con el dia seleccionado");
                }
                while (dates.size() < count) {
                    dates.add(cursor);
                    cursor = cursor.plusWeeks(1);
                }
            }
            case BIWEEKLY -> {
                List<Integer> days = parseDaysOfMonth(dueDays);
                if (days.size() < 2) {
                    throw new InvalidSaleException("Debe seleccionar dos dias del mes");
                }
                if (!days.contains(firstDueDate.getDayOfMonth())) {
                    throw new InvalidSaleException("La fecha de primer vencimiento no coincide con los dias seleccionados");
                }
                while (dates.size() < count) {
                    dates.add(cursor);
                    cursor = nextDateForDaysOfMonth(cursor.plusDays(1), days);
                }
            }
            case MONTHLY -> {
                int day = parseDayOfMonth(dueDays.get(0));
                if (day != firstDueDate.getDayOfMonth()) {
                    throw new InvalidSaleException("La fecha de primer vencimiento no coincide con el dia seleccionado");
                }
                while (dates.size() < count) {
                    dates.add(cursor);
                    cursor = cursor.plusMonths(1).withDayOfMonth(day);
                }
            }
        }
        return dates;
    }

    private Set<DayOfWeek> parseDaysOfWeek(List<String> dueDays) {
        Set<DayOfWeek> days = new TreeSet<>(Comparator.comparingInt(DayOfWeek::getValue));
        for (String raw : dueDays) {
            DayOfWeek day = parseDayOfWeek(raw);
            days.add(day);
        }
        if (days.isEmpty()) {
            throw new InvalidSaleException("Debe seleccionar al menos un dia");
        }
        return days;
    }

    private DayOfWeek parseDayOfWeek(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidSaleException("Dia de la semana invalido");
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidSaleException("Dia de la semana invalido");
        }
    }

    private List<Integer> parseDaysOfMonth(List<String> rawDays) {
        List<Integer> days = new ArrayList<>();
        for (String raw : rawDays) {
            int day = parseDayOfMonth(raw);
            if (!days.contains(day)) {
                days.add(day);
            }
        }
        days.sort(Integer::compareTo);
        if (days.isEmpty()) {
            throw new InvalidSaleException("Debe seleccionar el dia de vencimiento");
        }
        return days;
    }

    private int parseDayOfMonth(String raw) {
        try {
            int day = Integer.parseInt(raw.trim());
            if (day < 1 || day > 28) {
                throw new InvalidSaleException("Dia de vencimiento invalido (1-28)");
            }
            return day;
        } catch (NumberFormatException ex) {
            throw new InvalidSaleException("Dia de vencimiento invalido (1-28)");
        }
    }

    private LocalDate nextDateForDaysOfMonth(LocalDate from, List<Integer> days) {
        int monthDay = from.getDayOfMonth();
        for (int day : days) {
            if (day >= monthDay) {
                return from.withDayOfMonth(day);
            }
        }
        LocalDate nextMonth = from.plusMonths(1);
        return nextMonth.withDayOfMonth(days.get(0));
    }

    private String formatNumber(String prefix, Long id) {
        if (id == null) {
            return null;
        }
        return prefix + String.format("%06d", id);
    }
}
