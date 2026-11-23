package com.sales.maidav.service.finance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class FinanceMethodSummary {
    private final String method;
    private final BigDecimal income;
    private final BigDecimal payments;

    public BigDecimal getDifference() {
        return income.subtract(payments);
    }

    public String getLabel() {
        return switch (method) {
            case "CASH" -> "Efectivo";
            case "BANK" -> "Banco";
            case "TRANSFER" -> "Transferencia";
            case "DEBIT" -> "Débito";
            default -> "Sin medio informado";
        };
    }
}
