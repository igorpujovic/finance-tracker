package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;

/**
 * One row of a monthly report's breakdown: what a category came to over the month.
 * <p>
 * A null {@code categoryId} is the uncategorized bucket, which has no category and
 * therefore no name and no type (ADR-0002). It is a row like any other, because the
 * rows are a partition of the month and dropping it would lose real money.
 */
public interface CategoryTotal {

    Long getCategoryId();

    String getCategoryName();

    /**
     * The category's type as stored, left as a string here because the projection is
     * mapped from a result set rather than through JPA's enum converter. The response
     * turns it back into a {@code CategoryType}.
     */
    String getCategoryType();

    BigDecimal getTotalRsd();
}
