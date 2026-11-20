package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.CategoryType;

import java.math.BigDecimal;

/**
 * Derived, never stored. ADR-0002: the sign of the amount is the only record of
 * whether money came in or went out, so there is nothing that can drift out of
 * step with it.
 */
public enum TransactionType {
    INCOME,
    EXPENSE;

    public static TransactionType of(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("A transaction amount must not be zero: it would have no type");
        }
        return amount.signum() > 0 ? INCOME : EXPENSE;
    }

    /**
     * ADR-0004's compatibility rule, stated once. A transaction of a given type can take
     * a category of exactly one type, and this answers both halves of the ADR: which
     * rules are candidates at all, and whether a category asked for by hand is allowed.
     */
    public boolean canTake(CategoryType categoryType) {
        return switch (this) {
            case INCOME -> categoryType == CategoryType.INCOME;
            case EXPENSE -> categoryType == CategoryType.EXPENSE;
        };
    }
}
