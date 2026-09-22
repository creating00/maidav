package com.sales.maidav.service.sale;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.*;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.sale.*;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final CreditPaymentRepository creditPaymentRepository;
    private final SaleSellerChangeRepository saleSellerChangeRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final CompanySettingsService companySettingsService;

    @Override
    public List<Sale> findAll() {
        if (!isCurrentUserAdmin()) {
            Long sellerId = currentUserId();
            return sellerId == null ? List.of() : saleRepository.findBySeller_IdOrderBySaleDateDescIdDesc(sellerId);
        }
        return saleRepository.findAllByOrderBySaleDateDescIdDesc();
    }

    @Override
    public Sale findById(Long id) {
        if (!isCurrentUserAdmin()) {
            Long sellerId = currentUserId();
            return saleRepository.findByIdAndSeller_Id(id, sellerId == null ? -1L : sellerId)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
        }
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
        return createSale(client, seller, paymentType, null, saleDate, firstDueDate, paymentFrequency, dueDays,
                discountAmount, weeksCount, null, null, items);
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
                           List<BigDecimal> manualInstallmentAmounts,
                           List<SaleItemInput> items) {
        return createSale(client, seller, paymentType, null, saleDate, firstDueDate, paymentFrequency, dueDays,
                discountAmount, weeksCount, manualInstallmentAmounts, null, items);
    }

    @Override
    public Sale createSale(Client client,
                           User seller,
                           PaymentType paymentType,
                           PaymentCollectionMethod paymentCollectionMethod,
                           LocalDate saleDate,
                           LocalDate firstDueDate,
                           PaymentFrequency paymentFrequency,
                           List<String> dueDays,
                           BigDecimal discountAmount,
                           Integer weeksCount,
                           List<BigDecimal> manualInstallmentAmounts,
                           List<BigDecimal> manualCashInstallmentAmounts,
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
        BigDecimal costTotal = BigDecimal.ZERO;

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
            costTotal = costTotal.add(resolveProductCost(product).multiply(BigDecimal.valueOf(input.getQuantity())));
        }

        validateMonthlyLongPlanEligibility(paymentType, paymentFrequency, weeksCount, costTotal);

        total = resolveManualSaleTotal(total, paymentType, manualInstallmentAmounts);
        if (paymentType == PaymentType.CREDIT && manualInstallmentAmounts != null) {
            applyTargetTotalToSaleItems(saleItems, total);
        }

        total = total.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El total no puede ser negativo");
        }

        Sale sale = new Sale();
        sale.setClient(client);
        sale.setSeller(seller);
        sale.setPaymentType(paymentType);
        sale.setPaymentCollectionMethod(resolvePaymentCollectionMethod(paymentType, paymentCollectionMethod));
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
            createAccountSchedule(sale, paymentFrequency, dueDays, firstDueDate, weeksCount,
                    manualInstallmentAmounts, manualCashInstallmentAmounts);
        }

        return sale;
    }

    @Override
    public Sale updateSale(Long saleId,
                           Client client,
                           User seller,
                           PaymentType paymentType,
                           LocalDate saleDate,
                           LocalDate firstDueDate,
                           PaymentFrequency paymentFrequency,
                           List<String> dueDays,
                           BigDecimal discountAmount,
                           Integer weeksCount,
                           List<SaleItemInput> items) {
        return updateSale(saleId, client, seller, paymentType, null, saleDate, firstDueDate, paymentFrequency, dueDays,
                discountAmount, weeksCount, null, null, items);
    }

    @Override
    public Sale updateSale(Long saleId,
                           Client client,
                           User seller,
                           PaymentType paymentType,
                           LocalDate saleDate,
                           LocalDate firstDueDate,
                           PaymentFrequency paymentFrequency,
                           List<String> dueDays,
                           BigDecimal discountAmount,
                           Integer weeksCount,
                           List<BigDecimal> manualInstallmentAmounts,
                           List<SaleItemInput> items) {
        return updateSale(saleId, client, seller, paymentType, null, saleDate, firstDueDate, paymentFrequency, dueDays,
                discountAmount, weeksCount, manualInstallmentAmounts, null, items);
    }

    @Override
    public Sale updateSale(Long saleId,
                           Client client,
                           User seller,
                           PaymentType paymentType,
                           PaymentCollectionMethod paymentCollectionMethod,
                           LocalDate saleDate,
                           LocalDate firstDueDate,
                           PaymentFrequency paymentFrequency,
                           List<String> dueDays,
                           BigDecimal discountAmount,
                           Integer weeksCount,
                           List<BigDecimal> manualInstallmentAmounts,
                           List<BigDecimal> manualCashInstallmentAmounts,
                           List<SaleItemInput> items) {

        Sale sale = findById(saleId);
        if (sale.getStatus() == SaleStatus.VOID) {
            throw new InvalidSaleException("No se puede editar una venta anulada");
        }

        // Validate inputs
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

        // Restore stock from old items
        List<SaleItem> oldItems = saleItemRepository.findBySale_IdOrderByIdAsc(saleId);
        for (SaleItem oldItem : oldItems) {
            Product product = oldItem.getProduct();
            product.setStockAvailable(product.getStockAvailable() + oldItem.getQuantity());
            productRepository.save(product);
        }

        // Delete old items
        saleItemRepository.deleteAll(oldItems);

        // If it was a credit sale, delete the associated credit account and installments
        if (sale.getPaymentType() == PaymentType.CREDIT) {
            deleteCreditAccountForSale(sale);
        }

        // Build new items
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;

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
            costTotal = costTotal.add(resolveProductCost(product).multiply(BigDecimal.valueOf(input.getQuantity())));
        }

        validateMonthlyLongPlanEligibility(paymentType, paymentFrequency, weeksCount, costTotal);

        total = resolveManualSaleTotal(total, paymentType, manualInstallmentAmounts);
        if (paymentType == PaymentType.CREDIT && manualInstallmentAmounts != null) {
            applyTargetTotalToSaleItems(saleItems, total);
        }

        total = total.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSaleException("El total no puede ser negativo");
        }

        // Update sale fields
        sale.setClient(client);
        sale.setSeller(seller);
        sale.setPaymentType(paymentType);
        sale.setPaymentCollectionMethod(resolvePaymentCollectionMethod(paymentType, paymentCollectionMethod));
        sale.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        sale.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        sale.setSaleDate(saleDate == null ? LocalDateTime.now() : saleDate.atStartOfDay());
        sale.setFirstDueDate(firstDueDate);
        saleRepository.save(sale);

        // Save new items and deduct stock
        for (SaleItem item : saleItems) {
            item.setSale(sale);
            saleItemRepository.save(item);
            Product product = item.getProduct();
            product.setStockAvailable(product.getStockAvailable() - item.getQuantity());
            productRepository.save(product);
        }

        // Create credit account if needed
        if (paymentType == PaymentType.CREDIT) {
            createAccountSchedule(sale, paymentFrequency, dueDays, firstDueDate, weeksCount,
                    manualInstallmentAmounts, manualCashInstallmentAmounts);
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
        voidCreditAccountForSale(sale);

        List<SaleItem> items = saleItemRepository.findBySale_IdOrderByIdAsc(id);
        for (SaleItem item : items) {
            Product product = item.getProduct();
            product.setStockAvailable(product.getStockAvailable() + item.getQuantity());
            productRepository.save(product);
        }
    }

    @Override
    public Sale changeSeller(Long saleId, User newSeller, User changedBy) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
        if (sale.getStatus() == SaleStatus.VOID) {
            throw new InvalidSaleException("No se puede cambiar el vendedor de una venta anulada");
        }
        if (newSeller == null) {
            throw new InvalidSaleException("Debe seleccionar un vendedor");
        }
        if (changedBy == null) {
            throw new InvalidSaleException("No se pudo identificar al usuario que realiza el cambio");
        }

        User currentSeller = sale.getSeller();
        if (currentSeller != null && currentSeller.getId() != null
                && currentSeller.getId().equals(newSeller.getId())) {
            throw new InvalidSaleException("La venta ya esta asignada a ese vendedor");
        }

        SaleSellerChange change = new SaleSellerChange();
        change.setSale(sale);
        change.setPreviousSeller(currentSeller);
        change.setNewSeller(newSeller);
        change.setChangedBy(changedBy);

        sale.setSeller(newSeller);
        saleRepository.save(sale);
        saleSellerChangeRepository.save(change);
        return sale;
    }

    @Override
    public List<SaleSellerChange> findSellerChanges(Long saleId) {
        findById(saleId);
        return saleSellerChangeRepository.findBySale_IdOrderByCreatedAtDescIdDesc(saleId);
    }

    private void voidCreditAccountForSale(Sale sale) {
        if (sale == null || sale.getId() == null) {
            return;
        }
        CreditAccount account = creditAccountRepository.findBySale_Id(sale.getId()).orElse(null);
        if (account == null || account.getStatus() == AccountStatus.VOID) {
            return;
        }

        account.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        account.setStatus(AccountStatus.VOID);

        List<CreditInstallment> installments =
                creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId());
        for (CreditInstallment installment : installments) {
            installment.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            installment.setStatus(InstallmentStatus.VOID);
            installment.setPaidAt(null);
            installment.setVoided(true);
            installment.setVoidedAt(LocalDateTime.now());
            if (installment.getVoidReason() == null || installment.getVoidReason().isBlank()) {
                installment.setVoidReason("Venta anulada");
            }
        }
    }

    private void deleteCreditAccountForSale(Sale sale) {
        if (sale == null || sale.getId() == null || sale.getPaymentType() != PaymentType.CREDIT) {
            return;
        }
        CreditAccount account = creditAccountRepository.findBySale_Id(sale.getId()).orElse(null);
        if (account == null) {
            return;
        }

        // Delete payments first (FK -> credit_accounts)
        List<CreditPayment> payments = creditPaymentRepository.findByAccount_IdOrderByPaidAtDescIdDesc(account.getId());
        creditPaymentRepository.deleteAll(payments);

        // Then installments (FK -> credit_accounts)
        List<CreditInstallment> installments =
                creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId());
        creditInstallmentRepository.deleteAll(installments);

        creditAccountRepository.delete(account);

        // Force flush so DELETEs execute before the new INSERT
        entityManager.flush();
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
                                       Integer weeksCount,
                                       List<BigDecimal> manualInstallmentAmounts,
                                       List<BigDecimal> manualCashInstallmentAmounts) {
        if (weeksCount == null || weeksCount < 1 || weeksCount > 365) {
            throw new InvalidSaleException("Cantidad de cuotas invalida (1-365)");
        }
        if (firstDueDate == null) {
            throw new InvalidSaleException("Fecha de primer vencimiento invalida");
        }

        List<BigDecimal> installmentAmounts = resolveInstallmentAmounts(
                sale.getTotalAmount(),
                weeksCount,
                manualInstallmentAmounts
        );
        List<BigDecimal> cashInstallmentAmounts = resolveManualCashInstallmentAmounts(
                weeksCount,
                manualCashInstallmentAmounts
        );

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

        BigDecimal accumulated = BigDecimal.ZERO;

        List<LocalDate> schedule = buildSchedule(paymentFrequency, dueDays, firstDueDate, weeksCount);

        for (int i = 1; i <= weeksCount; i++) {
            BigDecimal amount = installmentAmounts.get(i - 1);
            accumulated = accumulated.add(amount);

            CreditInstallment installment = new CreditInstallment();
            installment.setAccount(account);
            installment.setInstallmentNumber(i);
            installment.setDueDate(schedule.get(i - 1));
            installment.setAmount(amount);
            if (cashInstallmentAmounts != null) {
                installment.setCashAmount(cashInstallmentAmounts.get(i - 1));
            }
            installment.setPaidAmount(BigDecimal.ZERO);
            installment.setStatus(InstallmentStatus.PENDING);
            creditInstallmentRepository.save(installment);
        }
    }

    private PaymentCollectionMethod resolvePaymentCollectionMethod(PaymentType paymentType,
                                                                   PaymentCollectionMethod paymentCollectionMethod) {
        if (paymentType == PaymentType.CREDIT) {
            return null;
        }
        return paymentCollectionMethod == null ? PaymentCollectionMethod.BANK : paymentCollectionMethod;
    }

    private void validateMonthlyLongPlanEligibility(PaymentType paymentType,
                                                   PaymentFrequency paymentFrequency,
                                                   Integer weeksCount,
                                                   BigDecimal costTotal) {
        if (paymentType != PaymentType.CREDIT || paymentFrequency != PaymentFrequency.MONTHLY || weeksCount == null) {
            return;
        }
        CompanySettings settings = companySettingsService.getSettings();
        if (settings == null) {
            settings = new CompanySettings();
        }
        int monthlyLongCount = settings.getCalcMesesLargo() == null || settings.getCalcMesesLargo() < 1
                ? 8
                : settings.getCalcMesesLargo();
        BigDecimal minCost = settings.getCalcMonthlyLongMinCost();
        if (weeksCount != monthlyLongCount || minCost == null || minCost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal normalizedCostTotal = (costTotal == null ? BigDecimal.ZERO : costTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedMinCost = minCost.setScale(2, RoundingMode.HALF_UP);
        if (normalizedCostTotal.compareTo(normalizedMinCost) < 0) {
            throw new InvalidSaleException("El plan de " + monthlyLongCount
                    + " cuotas solo esta disponible desde un costo de $" + normalizedMinCost.toPlainString());
        }
    }

    private BigDecimal resolveProductCost(Product product) {
        if (product == null || product.getCost() == null || product.getCost().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return product.getCost().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveManualSaleTotal(BigDecimal currentTotal,
                                              PaymentType paymentType,
                                              List<BigDecimal> manualInstallmentAmounts) {
        if (paymentType != PaymentType.CREDIT || manualInstallmentAmounts == null) {
            return currentTotal;
        }
        BigDecimal manualTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BigDecimal rawAmount : manualInstallmentAmounts) {
            if (rawAmount == null) {
                throw new InvalidSaleException("Debe completar todos los importes manuales de las cuotas");
            }
            BigDecimal amount = rawAmount.setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleException("Los importes manuales de las cuotas deben ser mayores a cero");
            }
            manualTotal = manualTotal.add(amount);
        }
        return manualTotal.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyTargetTotalToSaleItems(List<SaleItem> saleItems, BigDecimal targetTotal) {
        if (saleItems == null || saleItems.isEmpty()) {
            return;
        }
        BigDecimal originalTotal = saleItems.stream()
                .map(SaleItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (originalTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal accumulated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < saleItems.size(); i++) {
            SaleItem item = saleItems.get(i);
            BigDecimal lineTotal;
            if (i == saleItems.size() - 1) {
                lineTotal = targetTotal.subtract(accumulated).setScale(2, RoundingMode.HALF_UP);
            } else {
                lineTotal = targetTotal
                        .multiply(item.getLineTotal())
                        .divide(originalTotal, 2, RoundingMode.HALF_UP);
                accumulated = accumulated.add(lineTotal).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal unitPrice = lineTotal
                    .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
        }
    }

    private List<BigDecimal> resolveManualCashInstallmentAmounts(int installmentsCount,
                                                                 List<BigDecimal> manualCashInstallmentAmounts) {
        if (manualCashInstallmentAmounts == null) {
            return null;
        }
        if (manualCashInstallmentAmounts.size() != installmentsCount) {
            throw new InvalidSaleException("La cantidad de importes efectivo debe coincidir con la cantidad de cuotas");
        }
        List<BigDecimal> normalizedAmounts = new ArrayList<>();
        for (BigDecimal rawAmount : manualCashInstallmentAmounts) {
            if (rawAmount == null) {
                throw new InvalidSaleException("Debe completar todos los importes efectivo de las cuotas");
            }
            BigDecimal amount = rawAmount.setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleException("Los importes efectivo de las cuotas deben ser mayores a cero");
            }
            normalizedAmounts.add(amount);
        }
        return normalizedAmounts;
    }

    private List<BigDecimal> resolveInstallmentAmounts(BigDecimal totalAmount,
                                                       int installmentsCount,
                                                       List<BigDecimal> manualInstallmentAmounts) {
        BigDecimal total = totalAmount.setScale(2, RoundingMode.HALF_UP);
        if (manualInstallmentAmounts == null) {
            BigDecimal regularAmount = total.divide(BigDecimal.valueOf(installmentsCount), 2, RoundingMode.HALF_UP);
            BigDecimal accumulated = BigDecimal.ZERO;
            List<BigDecimal> automaticAmounts = new ArrayList<>();
            for (int i = 1; i <= installmentsCount; i++) {
                BigDecimal amount = regularAmount;
                if (i == installmentsCount) {
                    amount = total.subtract(accumulated).setScale(2, RoundingMode.HALF_UP);
                }
                accumulated = accumulated.add(amount);
                automaticAmounts.add(amount);
            }
            return automaticAmounts;
        }

        if (manualInstallmentAmounts.size() != installmentsCount) {
            throw new InvalidSaleException("La cantidad de importes manuales debe coincidir con la cantidad de cuotas");
        }

        List<BigDecimal> normalizedAmounts = new ArrayList<>();
        BigDecimal manualTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BigDecimal rawAmount : manualInstallmentAmounts) {
            if (rawAmount == null) {
                throw new InvalidSaleException("Debe completar todos los importes manuales de las cuotas");
            }
            BigDecimal amount = rawAmount.setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSaleException("Los importes manuales de las cuotas deben ser mayores a cero");
            }
            normalizedAmounts.add(amount);
            manualTotal = manualTotal.add(amount);
        }

        return normalizedAmounts;
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
                if (firstDueDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    throw new InvalidSaleException("La primera cuota diaria no puede caer en domingo");
                }
                while (dates.size() < count) {
                    if (cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
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
                    cursor = withDayOfMonthSafe(cursor.plusMonths(1), day);
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
            if (day < 1 || day > 31) {
                throw new InvalidSaleException("Dia de vencimiento invalido (1-31)");
            }
            return day;
        } catch (NumberFormatException ex) {
            throw new InvalidSaleException("Dia de vencimiento invalido (1-31)");
        }
    }

    private LocalDate nextDateForDaysOfMonth(LocalDate from, List<Integer> days) {
        int monthDay = from.getDayOfMonth();
        for (int day : days) {
            if (day >= monthDay) {
                return withDayOfMonthSafe(from, day);
            }
        }
        LocalDate nextMonth = from.plusMonths(1);
        return withDayOfMonthSafe(nextMonth, days.get(0));
    }

    private LocalDate withDayOfMonthSafe(LocalDate baseDate, int desiredDay) {
        return baseDate.withDayOfMonth(Math.min(desiredDay, baseDate.lengthOfMonth()));
    }

    private String formatNumber(String prefix, Long id) {
        if (id == null) {
            return null;
        }
        return prefix + String.format("%06d", id);
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }
}
