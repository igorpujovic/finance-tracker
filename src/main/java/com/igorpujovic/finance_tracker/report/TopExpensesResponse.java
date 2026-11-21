package com.igorpujovic.finance_tracker.report;

import java.time.LocalDate;
import java.util.List;

/**
 * The largest expenses of a period, most negative first.
 * <p>
 * The window and the {@code limit} come back with them, and the limit is the one that
 * was actually applied rather than the one that was asked for: somebody who asks for
 * 500 rows is told that 100 is what a report gives, instead of being left to wonder
 * whether there were only 100 expenses.
 */
public record TopExpensesResponse(
        LocalDate from,
        LocalDate to,
        int limit,
        List<TopExpenseResponse> expenses) {

    static TopExpensesResponse of(LocalDate from, LocalDate to, int limit, List<TopExpense> rows) {
        return new TopExpensesResponse(from, to, limit,
                rows.stream().map(TopExpenseResponse::of).toList());
    }
}
