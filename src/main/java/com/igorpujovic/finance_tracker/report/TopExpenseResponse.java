package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One ranked expense. {@code amount} and {@code amountRsd} are negative, as they are
 * everywhere else (ADR-0002); a null category is the uncategorized bucket, whose
 * spending is ranked with the rest.
 */
public record TopExpenseResponse(
        Long id,
        LocalDate date,
        String description,
        BigDecimal amount,
        String currency,
        BigDecimal amountRsd,
        Long categoryId,
        String categoryName) {

    static TopExpenseResponse of(TopExpense expense) {
        return new TopExpenseResponse(
                expense.getId(),
                expense.getDate(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getAmountRsd(),
                expense.getCategoryId(),
                expense.getCategoryName());
    }
}
