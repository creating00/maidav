package com.sales.maidav.service.quote;

import com.sales.maidav.model.quote.QuotePlanType;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.service.sale.CreditPaymentPricingSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class QuoteCalculator {

    public QuoteCalculatorConfig readConfig(CompanySettings settings) {
        CompanySettings safeSettings = settings == null ? new CompanySettings() : settings;
        return new QuoteCalculatorConfig(
                decimal(safeSettings.getCalcMultContado(), "1.30"),
                decimal(safeSettings.getCalcMultDebito(), "1.50"),
                decimal(safeSettings.getCalcRecargo(), "1.26"),
                integer(safeSettings.getCalcDias(), 144),
                decimal(safeSettings.getCalcIntDia(), "2.00"),
                integer(safeSettings.getCalcSemanas(), 13),
                decimal(safeSettings.getCalcIntSem(), "2.00"),
                integer(safeSettings.getCalcMesesCorto(), 4),
                decimal(safeSettings.getCalcIntMesCorto(), "2.00"),
                integer(safeSettings.getCalcMesesLargo(), 8),
                decimal(safeSettings.getCalcIntMesLargo(), "2.50")
        );
    }

    public List<QuotePlanSnapshot> calculatePlanSnapshots(BigDecimal baseAmount, CompanySettings settings) {
        BigDecimal normalizedBase = normalize(baseAmount);
        QuoteCalculatorConfig cfg = readConfig(settings);
        BigDecimal recargo = cfg.recargo().compareTo(BigDecimal.ZERO) > 0 ? cfg.recargo() : new BigDecimal("1.26");

        BigDecimal dailyFee = installmentAmount(normalizedBase, cfg.intDia(), recargo, cfg.dias());
        BigDecimal weeklyFee = installmentAmount(normalizedBase, cfg.intSem(), recargo, cfg.semanas());
        BigDecimal monthlyShortFee = installmentAmount(normalizedBase, cfg.intMesCorto(), recargo, cfg.mesesCorto());
        BigDecimal monthlyLongFee = installmentAmount(normalizedBase, cfg.intMesLargo(), recargo, cfg.mesesLargo());

        BigDecimal weeklyCash = cashInstallmentAmount(weeklyFee, recargo);
        BigDecimal monthlyShortCash = cashInstallmentAmount(monthlyShortFee, recargo);
        BigDecimal monthlyLongCash = cashInstallmentAmount(monthlyLongFee, recargo);

        return List.of(
                new QuotePlanSnapshot(
                        QuotePlanType.DAILY,
                        "Son " + cfg.dias() + " dias de $" + amountText(dailyFee),
                        null,
                        cfg.dias(),
                        dailyFee,
                        null,
                        1
                ),
                new QuotePlanSnapshot(
                        QuotePlanType.WEEKLY,
                        "Son " + cfg.semanas() + " semanas de $" + amountText(weeklyFee),
                        "Si abonas en efectivo pagas la cuota $" + amountText(weeklyCash),
                        cfg.semanas(),
                        weeklyFee,
                        weeklyCash,
                        2
                ),
                new QuotePlanSnapshot(
                        QuotePlanType.MONTHLY_SHORT,
                        "Son " + cfg.mesesCorto() + " meses de $" + amountText(monthlyShortFee),
                        "Si abonas en efectivo pagas la cuota $" + amountText(monthlyShortCash),
                        cfg.mesesCorto(),
                        monthlyShortFee,
                        monthlyShortCash,
                        3
                ),
                new QuotePlanSnapshot(
                        QuotePlanType.MONTHLY_LONG,
                        "Son " + cfg.mesesLargo() + " meses de $" + amountText(monthlyLongFee),
                        "Si abonas en efectivo pagas la cuota $" + amountText(monthlyLongCash),
                        cfg.mesesLargo(),
                        monthlyLongFee,
                        monthlyLongCash,
                        4
                )
        );
    }

    public BigDecimal calculateCashTotal(BigDecimal baseAmount, CompanySettings settings) {
        BigDecimal normalizedBase = normalize(baseAmount);
        QuoteCalculatorConfig cfg = readConfig(settings);
        return CreditPaymentPricingSupport.roundUpToFifty(normalizedBase.multiply(cfg.multContado()));
    }

    public BigDecimal calculateDebitTotal(BigDecimal baseAmount, CompanySettings settings) {
        BigDecimal normalizedBase = normalize(baseAmount);
        QuoteCalculatorConfig cfg = readConfig(settings);
        return CreditPaymentPricingSupport.roundUpToFifty(normalizedBase.multiply(cfg.multDebito()));
    }

    private BigDecimal installmentAmount(BigDecimal baseAmount,
                                         BigDecimal multiplier,
                                         BigDecimal recargo,
                                         Integer installments) {
        if (installments == null || installments < 1) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rawAmount = baseAmount
                .multiply(normalize(multiplier))
                .multiply(recargo)
                .divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
        return CreditPaymentPricingSupport.roundUpToFifty(rawAmount);
    }

    private BigDecimal cashInstallmentAmount(BigDecimal financedAmount, BigDecimal recargo) {
        if (financedAmount == null || financedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return CreditPaymentPricingSupport.roundUpToFifty(
                financedAmount.divide(recargo, 2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal decimal(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value.setScale(4, RoundingMode.HALF_UP);
    }

    private Integer integer(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String amountText(BigDecimal amount) {
        BigDecimal normalized = normalize(amount).stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }
}
