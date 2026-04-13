package com.sales.maidav.service.quote;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.QuotePriceMode;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class QuotePricingSupport {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private QuotePricingSupport() {
    }

    static BigDecimal resolveVisibleUnitPrice(Product product, QuotePriceMode priceMode) {
        BigDecimal price = priceMode == QuotePriceMode.WHOLESALE
                ? product.getPriceWholesale()
                : product.getPriceRetail();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuoteException("El producto no tiene precio disponible para el presupuesto");
        }
        return scaled(price);
    }

    static BigDecimal resolveFinancingUnitPrice(Product product, QuotePriceMode priceMode) {
        BigDecimal financingBase = financingBaseFromCostVat(product);
        return financingBase != null ? financingBase : resolveVisibleUnitPrice(product, priceMode);
    }

    private static BigDecimal financingBaseFromCostVat(Product product) {
        if (product == null) {
            return null;
        }
        BigDecimal cost = product.getCost();
        BigDecimal vatRate = product.getVatRate();
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0 || vatRate == null) {
            return null;
        }
        BigDecimal amount = cost
                .multiply(HUNDRED.add(vatRate))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return amount.compareTo(BigDecimal.ZERO) > 0 ? scaled(amount) : null;
    }

    private static BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
