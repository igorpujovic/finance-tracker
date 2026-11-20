package com.igorpujovic.finance_tracker.transaction;

/**
 * Where a transaction came from. ADR-0003: an imported transaction has a
 * fingerprint identifying its source line; a manual one has none.
 */
public enum TransactionSource {
    MANUAL,
    IMPORT
}
