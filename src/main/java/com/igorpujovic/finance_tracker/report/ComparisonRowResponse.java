package com.igorpujovic.finance_tracker.report;

import com.igorpujovic.finance_tracker.category.CategoryType;

import java.math.BigDecimal;

/**
 * One line of a comparison. {@code percent} is absent when the previous month had
 * nothing in the category, and a positive {@code change} means the position improved
 * whichever type the category is (ADR-0002).
 */
public record ComparisonRowResponse(
        Long categoryId,
        String categoryName,
        CategoryType categoryType,
        BigDecimal current,
        BigDecimal previous,
        BigDecimal change,
        BigDecimal percent) {

    static ComparisonRowResponse of(CategoryComparison comparison) {
        String type = comparison.getCategoryType();
        return new ComparisonRowResponse(
                comparison.getCategoryId(),
                comparison.getCategoryName(),
                type == null ? null : CategoryType.valueOf(type),
                comparison.getCurrentTotal(),
                comparison.getPreviousTotal(),
                comparison.getChange(),
                comparison.getPercent());
    }
}
