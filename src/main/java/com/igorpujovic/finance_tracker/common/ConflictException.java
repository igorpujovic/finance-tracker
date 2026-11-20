package com.igorpujovic.finance_tracker.common;

/**
 * The request is well formed but contradicts something already true — a category
 * still in use, or a category whose type cannot take this transaction.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
