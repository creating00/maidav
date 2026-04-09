package com.sales.maidav.service.quote;

import java.math.BigDecimal;

public record QuoteCalculatorConfig(
        BigDecimal multContado,
        BigDecimal multDebito,
        BigDecimal recargo,
        Integer dias,
        BigDecimal intDia,
        Integer semanas,
        BigDecimal intSem,
        Integer mesesCorto,
        BigDecimal intMesCorto,
        Integer mesesLargo,
        BigDecimal intMesLargo
) {
}
