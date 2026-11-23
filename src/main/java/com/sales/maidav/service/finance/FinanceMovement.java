package com.sales.maidav.service.finance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public class FinanceMovement {
    private final LocalDate date;
    private final String kind;
    private final Long id;
    private final Long sourceId;
    private final String reference;
    private final String paymentMethod;
    private final BigDecimal amount;

    public String getKindLabel() {
        return switch (kind) {
            case "CASH_SALE" -> "Venta de contado";
            case "CREDIT_PAYMENT" -> "Cobro de crédito";
            case "CREDIT_REVERSAL" -> "Reversión de cobro";
            case "PURCHASE" -> "Compra";
            case "EXPENSE" -> "Gasto";
            default -> throw new IllegalStateException("Tipo de movimiento desconocido");
        };
    }

    public String getDetailPath() {
        return switch (kind) {
            case "CASH_SALE" -> "/sales/" + sourceId;
            case "CREDIT_PAYMENT", "CREDIT_REVERSAL" -> "/accounts/" + sourceId;
            case "PURCHASE" -> "/purchases/" + sourceId;
            case "EXPENSE" -> "/expenses/" + sourceId;
            default -> throw new IllegalStateException("Tipo de movimiento desconocido");
        };
    }

    public String getRequiredPermission() {
        return switch (kind) {
            case "CASH_SALE" -> "SALES_READ";
            case "CREDIT_PAYMENT", "CREDIT_REVERSAL" -> "ARREARS_READ";
            case "PURCHASE" -> "PURCHASE_READ";
            case "EXPENSE" -> "EXPENSE_READ";
            default -> throw new IllegalStateException("Tipo de movimiento desconocido");
        };
    }
}
