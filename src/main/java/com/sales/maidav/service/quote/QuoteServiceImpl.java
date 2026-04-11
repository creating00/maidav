package com.sales.maidav.service.quote;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuoteItem;
import com.sales.maidav.model.quote.QuotePlanOption;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.quote.QuoteRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class QuoteServiceImpl implements QuoteService {

    private final QuoteRepository quoteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CompanySettingsService companySettingsService;
    private final QuoteCalculator quoteCalculator;

    @Override
    public List<Quote> findAll() {
        Long sellerId = currentUserId();
        return sellerId == null ? List.of() : quoteRepository.findBySeller_IdOrderByCreatedAtDescIdDesc(sellerId);
    }

    @Override
    public Quote findById(Long id) {
        Long sellerId = currentUserId();
        if (sellerId == null) {
            throw new RuntimeException("Presupuesto no encontrado");
        }
        Quote quote = quoteRepository.findByIdAndSeller_Id(id, sellerId)
                .orElseThrow(() -> new RuntimeException("Presupuesto no encontrado"));
        quote.getItems().size();
        quote.getPlanOptions().size();
        return quote;
    }

    @Override
    public Quote create(QuotePriceMode priceMode, List<QuoteItemInput> inputs) {
        Quote quote = buildQuote(priceMode, inputs);
        quoteRepository.save(quote);
        if (quote.getQuoteNumber() == null || quote.getQuoteNumber().isBlank()) {
            quote.setQuoteNumber(formatNumber("P-", quote.getId()));
        }
        return quote;
    }

    private Quote buildQuote(QuotePriceMode priceMode, List<QuoteItemInput> inputs) {
        if (priceMode == null) {
            throw new InvalidQuoteException("Debe seleccionar el tipo de precio");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidQuoteException("Debe agregar al menos un producto");
        }

        User seller = currentUser();
        CompanySettings settings = companySettingsService.getSettings();

        List<QuoteItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int index = 0; index < inputs.size(); index++) {
            QuoteItemInput input = inputs.get(index);
            if (input.productId() == null || input.quantity() == null) {
                throw new InvalidQuoteException("Producto y cantidad son obligatorios");
            }
            if (input.quantity() <= 0) {
                throw new InvalidQuoteException("La cantidad debe ser mayor a cero");
            }

            Product product = productRepository.findById(input.productId())
                    .orElseThrow(() -> new InvalidQuoteException("Producto no encontrado"));

            BigDecimal unitPrice = resolveUnitPrice(product, priceMode);
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(input.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            QuoteItem item = new QuoteItem();
            item.setProduct(product);
            item.setDisplayOrder(index + 1);
            item.setProductCode(product.getProductCode());
            item.setProductDescription(product.getDescription());
            item.setProductImagePath(product.getImagePath());
            item.setQuantity(input.quantity());
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            items.add(item);

            totalAmount = totalAmount.add(lineTotal);
        }

        BigDecimal pricingBaseAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        Quote quote = new Quote();
        quote.setSeller(seller);
        quote.setPriceMode(priceMode);
        quote.setItemCount(items.size());
        quote.setProductSummary(buildProductSummary(items));
        quote.setPricingBaseAmount(pricingBaseAmount);
        quote.setCashAmount(quoteCalculator.calculateCashTotal(pricingBaseAmount, settings));
        quote.setDebitAmount(quoteCalculator.calculateDebitTotal(pricingBaseAmount, settings));
        quote.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));

        for (QuoteItem item : items) {
            quote.addItem(item);
        }

        for (QuotePlanSnapshot snapshot : quoteCalculator.calculatePlanSnapshots(pricingBaseAmount, settings)) {
            QuotePlanOption option = new QuotePlanOption();
            option.setDisplayOrder(snapshot.displayOrder());
            option.setPlanType(snapshot.planType());
            option.setTitle(snapshot.title());
            option.setPromoText(snapshot.promoText());
            option.setInstallmentCount(snapshot.installmentCount());
            option.setFeeAmount(snapshot.feeAmount());
            option.setCashFeeAmount(snapshot.cashFeeAmount());
            quote.addPlanOption(option);
        }
        return quote;
    }

    @Override
    public void delete(Long id) {
        Quote quote = findById(id);
        quoteRepository.delete(quote);
    }

    @Override
    public String previewNextQuoteNumber() {
        return formatNumber("P-", quoteRepository.nextQuoteId());
    }

    private BigDecimal resolveUnitPrice(Product product, QuotePriceMode priceMode) {
        BigDecimal price = priceMode == QuotePriceMode.WHOLESALE
                ? product.getPriceWholesale()
                : product.getPriceRetail();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuoteException("El producto no tiene precio disponible para el presupuesto");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildProductSummary(List<QuoteItem> items) {
        LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        for (QuoteItem item : items) {
            if (item.getProductDescription() != null && !item.getProductDescription().isBlank()) {
                descriptions.add(item.getProductDescription().trim());
            }
        }
        List<String> orderedDescriptions = new ArrayList<>(descriptions);
        if (orderedDescriptions.isEmpty()) {
            return "Sin productos";
        }
        if (orderedDescriptions.size() <= 2) {
            return String.join(", ", orderedDescriptions);
        }
        return orderedDescriptions.get(0) + ", " + orderedDescriptions.get(1) + " +" + (orderedDescriptions.size() - 2);
    }

    private String formatNumber(String prefix, Long id) {
        if (id == null) {
            return null;
        }
        return prefix + String.format("%06d", id);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new InvalidQuoteException("No se pudo identificar al vendedor actual");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidQuoteException("No se pudo identificar al vendedor actual"));
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
