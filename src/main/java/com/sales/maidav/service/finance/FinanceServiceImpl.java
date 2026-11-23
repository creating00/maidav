package com.sales.maidav.service.finance;

import com.sales.maidav.repository.finance.FinanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {
    private final FinanceRepository repository;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinanceReport buildReport(LocalDate from, LocalDate to, int page) {
        if (from == null || to == null || from.getYear() < 1 || from.isAfter(to) || to.isAfter(LocalDate.now())) {
            throw new InvalidFinancePeriodException("Seleccione un período válido: desde no puede superar a hasta y las fechas deben ser hasta hoy");
        }
        var aggregates = repository.aggregate(from, to);
        long total = aggregates.stream().mapToLong(FinanceAggregate::getCount).sum();
        int lastPage = total == 0 ? 0 : (int) Math.min((total - 1) / 20, Integer.MAX_VALUE / 20);
        int safePage = Math.max(0, Math.min(page, lastPage));
        var movements = new PageImpl<>(repository.findMovements(from, to, safePage * 20, 20), PageRequest.of(safePage, 20), total);
        return new FinanceReport(from, to, aggregates, repository.exclusions(from, to), movements);
    }
}
