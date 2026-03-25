package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.PaymentFrequency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class CreditPaymentPricingSupport {

    private CreditPaymentPricingSupport() {
    }

    public static boolean usesCashValue(PaymentFrequency frequency, LocalDate dueDate, LocalDate paymentDate) {
        if (frequency == PaymentFrequency.DAILY) {
            // EXCEPCION PLAN DIARIO
            return false;
        }
        if (dueDate == null || paymentDate == null) {
            return false;
        }
        return !paymentDate.isAfter(dueDate);
    }

    public static BigDecimal resolveCollectedAmountDue(BigDecimal financedRemaining,
                                                       BigDecimal cashRecargo,
                                                       PaymentFrequency frequency,
                                                       LocalDate dueDate,
                                                       LocalDate paymentDate) {
        BigDecimal normalizedRemaining = normalize(financedRemaining);
        if (normalizedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (usesCashValue(frequency, dueDate, paymentDate)) {
            // PAGO EN FECHA COMO CONTADO
            return roundUpToFifty(
                    normalizedRemaining.divide(normalizeRecargo(cashRecargo), 2, RoundingMode.HALF_UP)
            );
        }
        // PAGO FUERA DE FECHA COMO FINANCIADO
        return normalizedRemaining;
    }

    public static BigDecimal resolveInstallmentCashValue(BigDecimal financedAmount,
                                                         BigDecimal cashRecargo,
                                                         PaymentFrequency frequency) {
        BigDecimal normalizedAmount = normalize(financedAmount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0 || frequency == PaymentFrequency.DAILY) {
            // EXCEPCION PLAN DIARIO
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return roundUpToFifty(
                normalizedAmount.divide(normalizeRecargo(cashRecargo), 2, RoundingMode.HALF_UP)
        );
    }

    public static BigDecimal resolveImpactAmount(BigDecimal financedRemaining,
                                                 BigDecimal collectedAmount,
                                                 BigDecimal cashRecargo,
                                                 PaymentFrequency frequency,
                                                 LocalDate dueDate,
                                                 LocalDate paymentDate) {
        BigDecimal normalizedRemaining = normalize(financedRemaining);
        BigDecimal normalizedCollected = normalize(collectedAmount);
        if (normalizedRemaining.compareTo(BigDecimal.ZERO) <= 0 || normalizedCollected.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (!usesCashValue(frequency, dueDate, paymentDate)) {
            // PAGO FUERA DE FECHA COMO FINANCIADO
            return normalizedCollected.min(normalizedRemaining).setScale(2, RoundingMode.HALF_UP);
        }

        // PAGO EN FECHA COMO CONTADO
        BigDecimal collectedNeeded = resolveCollectedAmountDue(
                normalizedRemaining,
                cashRecargo,
                frequency,
                dueDate,
                paymentDate
        );
        if (collectedNeeded.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = normalizedCollected
                .divide(collectedNeeded, 8, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        return normalizedRemaining
                .multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP)
                .min(normalizedRemaining);
    }

    public static BigDecimal roundUpToFifty(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = new BigDecimal("50");
        return amount
                .divide(factor, 0, RoundingMode.CEILING)
                .multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeRecargo(BigDecimal recargo) {
        if (recargo == null || recargo.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1.26");
        }
        return recargo.setScale(4, RoundingMode.HALF_UP);
    }
}
