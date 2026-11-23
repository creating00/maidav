package com.sales.maidav.service.finance;

import java.time.LocalDate;

public interface FinanceService {
    FinanceReport buildReport(LocalDate from, LocalDate to, int page);
}
