package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.category.Category;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code amount} and {@code amountRsd} are signed: an expense is a negative number
 * here exactly as it is in the database and in every report (ADR-0002).
 */
public record TransactionResponse(
        Long id,
        LocalDate date,
        String description,
        BigDecimal amount,
        String currency,
        BigDecimal amountRsd,
        TransactionType type,
        Long categoryId,
        String categoryName,
        TransactionSource source,
        Long importBatchId,
        Instant createdAt) {

    public static TransactionResponse of(Transaction transaction) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getAmountRsd(),
                transaction.getType(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                transaction.getSource(),
                transaction.getImportBatchId(),
                transaction.getCreatedAt());
    }
}
