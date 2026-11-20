package com.igorpujovic.finance_tracker.category;

/**
 * Which side of the ledger a category belongs to. A category is one or the other,
 * never both — see ADR-0004 for what that costs and why it is worth it.
 */
public enum CategoryType {
    INCOME,
    EXPENSE
}
