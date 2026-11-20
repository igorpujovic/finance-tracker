package com.igorpujovic.finance_tracker.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure logic, so no database. ADR-0006 keeps tests like this one out of the
 * container: there is nothing here a real PostgreSQL would reveal.
 */
class TransactionTypeTest {

    @Test
    @DisplayName("a negative amount is an expense and a positive one is income")
    void readsTypeFromTheSign() {
        assertThat(TransactionType.of(new BigDecimal("-2450.80"))).isEqualTo(TransactionType.EXPENSE);
        assertThat(TransactionType.of(new BigDecimal("95000.00"))).isEqualTo(TransactionType.INCOME);
    }

    @Test
    @DisplayName("a trailing-zero amount is still compared by sign, not by equals")
    void treatsScaledZeroAsZero() {
        assertThatThrownBy(() -> TransactionType.of(new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("zero has no type")
    void refusesZero() {
        assertThatThrownBy(() -> TransactionType.of(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be zero");
    }
}
