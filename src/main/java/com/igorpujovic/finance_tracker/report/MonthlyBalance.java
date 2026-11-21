package com.igorpujovic.finance_tracker.report;

import java.math.BigDecimal;

/**
 * A month's balance and the two halves it is made of, summed over every transaction in
 * the month regardless of category.
 * <p>
 * GLOSSARY: a balance is income plus expenses over a period, and because expenses are
 * negative (ADR-0002) it is simply the sum of the dinar amounts. So
 * {@code income + expenses = balance} holds as written, with no sign flip the reader has
 * to know about.
 * <p>
 * Asked for separately from the per-category breakdown rather than added up from it,
 * because the uncategorized bucket mixes income and expenses into a single row and the
 * split cannot be recovered from its total.
 */
public interface MonthlyBalance {

    BigDecimal getIncome();

    BigDecimal getExpenses();

    BigDecimal getBalance();
}
