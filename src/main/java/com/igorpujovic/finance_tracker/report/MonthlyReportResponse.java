package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * A monthly report: the month's three figures and the categories they are made of.
 * <p>
 * Two promises hold over this shape, and both are asserted by tests rather than
 * documented and hoped for. {@code income + expenses = balance}, because expenses are
 * negative (ADR-0002). And the {@code categories} totals sum to {@code balance},
 * because they are a complete partition of the month, uncategorized row included.
 */
public record MonthlyReportResponse(
        int year,
        int month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal balance,
        List<CategoryTotalResponse> categories) {

    static MonthlyReportResponse of(ReportMonth month, MonthlyBalance balance, List<CategoryTotal> rows) {
        return new MonthlyReportResponse(
                month.year(),
                month.month(),
                balance.getIncome(),
                balance.getExpenses(),
                balance.getBalance(),
                rows.stream().map(CategoryTotalResponse::of).toList());
    }
}
