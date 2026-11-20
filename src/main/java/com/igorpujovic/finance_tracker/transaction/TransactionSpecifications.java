package com.igorpujovic.finance_tracker.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filters are composed as specifications rather than written as one query with
 * {@code :param is null or ...} branches: an absent filter contributes no predicate
 * at all, so the database is never asked to reason about a null parameter's type.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Transaction> onOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<Transaction> onOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }

    public static Specification<Transaction> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> uncategorized() {
        return (root, query, cb) -> cb.isNull(root.get("category"));
    }

    /**
     * ADR-0002: type is the sign of the amount, so filtering by type is a comparison
     * against zero rather than a lookup of a stored column.
     */
    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> type == TransactionType.INCOME
                ? cb.greaterThan(root.get("amount"), BigDecimal.ZERO)
                : cb.lessThan(root.get("amount"), BigDecimal.ZERO);
    }
}
