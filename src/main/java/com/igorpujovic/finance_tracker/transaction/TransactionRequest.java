package com.igorpujovic.finance_tracker.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @NotBlank @Size(max = 255) String description,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        Long categoryId) {

    /**
     * ADR-0002: zero has no sign and therefore no type. The original specification
     * refused it only during import; refusing it on the request as well is what stops
     * the manual endpoint reopening the hole the importer closes. The database holds
     * the same rule as a check constraint.
     */
    @JsonIgnore
    @AssertTrue(message = "must not be zero")
    public boolean isAmountNonZero() {
        return amount == null || amount.signum() != 0;
    }

    public String normalisedCurrency() {
        return currency == null ? null : currency.toUpperCase();
    }
}
