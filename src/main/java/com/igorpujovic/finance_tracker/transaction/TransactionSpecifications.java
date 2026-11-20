package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.Category_;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filters are composed as specifications rather than written as one query with
 * {@code :param is null or ...} branches: an absent filter contributes no predicate
 * at all, so the database is never asked to reason about a null parameter's type.
 * <p>
 * Properties are named through the generated static metamodel rather than as string
 * literals, so {@code root.get(...)} is checked by the compiler and returns a typed
 * path. Renaming a field on the entity breaks this file at build time instead of
 * producing a query that fails on the first request that uses the filter.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Transaction> onOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(Transaction_.date), from);
    }

    public static Specification<Transaction> onOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(Transaction_.date), to);
    }

    public static Specification<Transaction> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(
                root.get(Transaction_.category).get(Category_.id), categoryId);
    }

    public static Specification<Transaction> uncategorized() {
        return (root, query, cb) -> cb.isNull(root.get(Transaction_.category));
    }

    /**
     * ADR-0002: type is the sign of the amount, so filtering by type is a comparison
     * against zero rather than a lookup of a stored column.
     */
    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> type == TransactionType.INCOME
                ? cb.greaterThan(root.get(Transaction_.amount), BigDecimal.ZERO)
                : cb.lessThan(root.get(Transaction_.amount), BigDecimal.ZERO);
    }
}
