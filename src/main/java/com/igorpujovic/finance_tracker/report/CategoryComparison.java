package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;

/**
 * One category set against itself a month earlier. Both totals are zero-filled, so a
 * row exists for a category that was active in only one of the two months.
 * <p>
 * {@code percent} is null when the previous total is zero, because there is nothing to
 * be a percentage of. Everything else about the sign convention is ADR-0002: change is
 * {@code current - previous} over income and expenses alike, and the percent divides by
 * the magnitude of the previous total, so a positive number always means the position
 * improved.
 */
public interface CategoryComparison {

    Long getCategoryId();

    String getCategoryName();

    String getCategoryType();

    BigDecimal getCurrentTotal();

    BigDecimal getPreviousTotal();

    BigDecimal getChange();

    BigDecimal getPercent();
}
