package com.sales.maidav.repository.finance;

import com.sales.maidav.service.finance.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class FinanceRepository {
    @PersistenceContext
    private EntityManager entityManager;

    // One row per source record. Never join sale items, installments or payment allocations here.
    private static final String MOVEMENTS = """
            with movements as (
                select cast(s.sale_date as date) movement_date, 'CASH_SALE' kind, s.id, s.id source_id,
                       coalesce(s.sale_number, 'V-' || s.id) reference,
                       coalesce(s.payment_collection_method, 'UNSPECIFIED') payment_method, s.total_amount amount
                from sales s where s.payment_type = 'CASH' and s.status = 'ACTIVE'
                  and s.sale_date >= :startAt and s.sale_date < :endAt
                union all
                select p.paid_at, case when p.reversal then 'CREDIT_REVERSAL' else 'CREDIT_PAYMENT' end,
                       p.id, p.account_id, 'P-' || p.id, coalesce(p.payment_method, 'UNSPECIFIED'), p.amount
                from credit_payments p join credit_accounts a on a.id = p.account_id join sales s on s.id = a.sale_id
                where a.status <> 'VOID' and s.status = 'ACTIVE' and s.payment_type = 'CREDIT'
                  and p.paid_at >= :fromDate and p.paid_at <= :toDate
                union all
                select p.purchase_date, 'PURCHASE', p.id, p.id, 'C-' || p.id, p.payment_method, -p.total_amount
                from purchases p where p.purchase_date >= :fromDate and p.purchase_date <= :toDate
                union all
                select e.expense_date, 'EXPENSE', e.id, e.id, 'G-' || e.id, e.payment_method, -e.amount
                from expenses e where e.expense_date >= :fromDate and e.expense_date <= :toDate
            )
            """;

    public List<FinanceAggregate> aggregate(LocalDate from, LocalDate to) {
        return rows(query(MOVEMENTS + "select kind, payment_method, sum(amount), count(*) from movements group by kind, payment_method", from, to))
                .stream().map(row -> new FinanceAggregate((String) row[0], (String) row[1], (BigDecimal) row[2], ((Number) row[3]).longValue())).toList();
    }

    public List<FinanceMovement> findMovements(LocalDate from, LocalDate to, int offset, int limit) {
        Query query = query(MOVEMENTS + "select movement_date, kind, id, source_id, reference, payment_method, amount from movements order by movement_date desc, kind, id desc", from, to);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return rows(query).stream().map(row -> new FinanceMovement(LocalDate.parse(row[0].toString()), (String) row[1],
                ((Number) row[2]).longValue(), ((Number) row[3]).longValue(), (String) row[4], (String) row[5], (BigDecimal) row[6])).toList();
    }

    public FinanceExclusions exclusions(LocalDate from, LocalDate to) {
        Query query = entityManager.createNativeQuery("""
                select
                    (select coalesce(sum(s.total_amount), 0) from sales s where s.status = 'ACTIVE' and s.sale_date >= :startAt and s.sale_date < :endAt),
                    (select count(*) from sales s where s.status = 'VOID' and s.payment_type = 'CASH' and s.sale_date >= :startAt and s.sale_date < :endAt),
                    (select coalesce(sum(s.total_amount), 0) from sales s where s.status = 'VOID' and s.payment_type = 'CASH' and s.sale_date >= :startAt and s.sale_date < :endAt),
                    (select count(*) from credit_payments p join credit_accounts a on a.id = p.account_id join sales s on s.id = a.sale_id
                        where (a.status = 'VOID' or s.status <> 'ACTIVE' or s.payment_type <> 'CREDIT') and p.paid_at >= :fromDate and p.paid_at <= :toDate),
                    (select coalesce(sum(p.amount), 0) from credit_payments p join credit_accounts a on a.id = p.account_id join sales s on s.id = a.sale_id
                        where (a.status = 'VOID' or s.status <> 'ACTIVE' or s.payment_type <> 'CREDIT') and p.paid_at >= :fromDate and p.paid_at <= :toDate)
                """);
        bind(query, from, to);
        Object[] row = (Object[]) query.getSingleResult();
        return new FinanceExclusions((BigDecimal) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2],
                ((Number) row[3]).longValue(), (BigDecimal) row[4]);
    }

    private Query query(String sql, LocalDate from, LocalDate to) {
        Query query = entityManager.createNativeQuery(sql);
        bind(query, from, to);
        return query;
    }

    private void bind(Query query, LocalDate from, LocalDate to) {
        query.setParameter("startAt", from.atStartOfDay());
        query.setParameter("endAt", to.plusDays(1).atStartOfDay());
        query.setParameter("fromDate", from);
        query.setParameter("toDate", to);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query query) {
        return query.getResultList();
    }
}
