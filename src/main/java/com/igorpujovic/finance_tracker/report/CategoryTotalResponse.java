package com.igorpujovic.finance_tracker.report;

import com.igorpujovic.finance_tracker.category.CategoryType;

import java.math.BigDecimal;

/**
 * One line of a monthly report. {@code totalRsd} is signed, so an expense category
 * reports a negative number (ADR-0002).
 * <p>
 * The uncategorized bucket comes back with all three category fields null. That is the
 * honest shape: it is money with no category rather than a category of its own, and a
 * reader can tell it apart from a category somebody happened to name Uncategorized.
 */
public record CategoryTotalResponse(
        Long categoryId,
        String categoryName,
        CategoryType categoryType,
        BigDecimal totalRsd) {

    static CategoryTotalResponse of(CategoryTotal total) {
        String type = total.getCategoryType();
        return new CategoryTotalResponse(
                total.getCategoryId(),
                total.getCategoryName(),
                type == null ? null : CategoryType.valueOf(type),
                total.getTotalRsd());
    }
}
