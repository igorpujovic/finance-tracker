package com.igorpujovic.finance_tracker.transaction;

/**
 * A null {@code categoryId} clears the category, returning the transaction to the
 * uncategorized queue. That is a legitimate thing to want, so it is not a validation
 * failure.
 */
public record TransactionCategoryRequest(Long categoryId) {
}
