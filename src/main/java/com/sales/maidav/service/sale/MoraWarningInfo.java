package com.sales.maidav.service.sale;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MoraWarningInfo(
        boolean configured,
        boolean hasPendingInstallment,
        boolean highlighted,
        String message,
        String tooltip,
        Long installmentId,
        Integer installmentNumber,
        LocalDate dueDate,
        long daysOverdue,
        long daysUntilDue,
        BigDecimal pendingAmount
) {

    public static MoraWarningInfo notConfigured(String tooltip) {
        return new MoraWarningInfo(false, false, false, "", tooltip, null, null, null, 0, 0, null);
    }

    public static MoraWarningInfo empty(boolean configured, String tooltip) {
        return new MoraWarningInfo(configured, false, false, "", tooltip, null, null, null, 0, 0, null);
    }
}
