package com.igorpujovic.finance_tracker.transaction;

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
}
