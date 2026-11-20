package com.igorpujovic.finance_tracker.imports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure logic, so no database. What a real PostgreSQL does with these values — two
 * nulls coexisting under the unique index, a re-upload colliding — is asserted in
 * {@code SchemaConstraintsTest} and {@code ImportApiTest} instead.
 */
class FingerprintTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);
    private static final String DESCRIPTION = "NETFLIX.COM, AMSTERDAM";
    private static final BigDecimal AMOUNT = new BigDecimal("-9.99");
    private static final String CURRENCY = "EUR";

    /**
     * The SHA-256 of {@code 2026-11-04|NETFLIX.COM, AMSTERDAM|-9.99|EUR}, computed
     * outside this codebase. It pins the hash so that a change to how a line is
     * canonicalised fails here rather than quietly re-importing every statement
     * anybody has ever uploaded.
     */
    private static final String KNOWN_FINGERPRINT =
            "72dbca5a7d3fd753248cb992ca489d8dc69fc4a12c3b52a412547788391b93d7";

    @Test
    @DisplayName("a line hashes to a value fixed outside this codebase, and fits the column")
    void matchesAKnownFingerprint() {
        String fingerprint = Fingerprint.of(DATE, DESCRIPTION, AMOUNT, CURRENCY);

        assertThat(fingerprint).isEqualTo(KNOWN_FINGERPRINT);
        assertThat(fingerprint).hasSize(64);
    }

    @Test
    @DisplayName("changing any one of the four fields changes the fingerprint")
    void dependsOnAllFourFields() {
        String original = Fingerprint.of(DATE, DESCRIPTION, AMOUNT, CURRENCY);

        assertThat(Fingerprint.of(DATE.plusDays(1), DESCRIPTION, AMOUNT, CURRENCY)).isNotEqualTo(original);
        assertThat(Fingerprint.of(DATE, "NETFLIX.COM, DUBLIN", AMOUNT, CURRENCY)).isNotEqualTo(original);
        assertThat(Fingerprint.of(DATE, DESCRIPTION, new BigDecimal("-10.99"), CURRENCY)).isNotEqualTo(original);
        assertThat(Fingerprint.of(DATE, DESCRIPTION, AMOUNT, "USD")).isNotEqualTo(original);
    }

    @Test
    @DisplayName("the same payment written with a different number of decimals is the same line")
    void ignoresTrailingZeroesInTheAmount() {
        assertThat(Fingerprint.of(DATE, DESCRIPTION, new BigDecimal("-9.990"), CURRENCY))
                .isEqualTo(Fingerprint.of(DATE, DESCRIPTION, AMOUNT, CURRENCY));
    }

    @Test
    @DisplayName("fields cannot be run together to forge a match across the separator")
    void keepsFieldsApart() {
        assertThat(Fingerprint.of(DATE, "MAXI", new BigDecimal("-1"), "RSD"))
                .isNotEqualTo(Fingerprint.of(DATE, "MAXI|-1", new BigDecimal("-1"), "RSD"));
    }
}
