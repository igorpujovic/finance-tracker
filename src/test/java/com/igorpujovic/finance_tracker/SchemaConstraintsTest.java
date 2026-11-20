package com.igorpujovic.finance_tracker;

import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules from ADR-0002 and ADR-0003 as the database holds them. These assertions
 * are the reason the suite runs against PostgreSQL: a mocked repository cannot observe
 * a check constraint, and nulls being distinct under a unique index is a property of
 * the database rather than of any Java code.
 */
@Transactional
class SchemaConstraintsTest extends PostgresTest {

    @Autowired
    JdbcTemplate jdbc;

    private static final String INSERT = """
            insert into transactions
                (date, description, amount, currency, amount_rsd, source, fingerprint)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    @Test
    @DisplayName("a zero amount has no sign and therefore no type, so it is refused")
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> jdbc.update(INSERT,
                LocalDate.of(2026, 11, 1), "ZERO", 0, "RSD", 0, "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an imported row without a fingerprint is refused")
    void rejectsImportedRowWithoutFingerprint() {
        assertThatThrownBy(() -> jdbc.update(INSERT,
                LocalDate.of(2026, 11, 1), "NO FINGERPRINT", -100, "RSD", -100, "IMPORT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a manual row carrying a fingerprint is refused")
    void rejectsManualRowWithFingerprint() {
        assertThatThrownBy(() -> jdbc.update(INSERT,
                LocalDate.of(2026, 11, 1), "UNEXPECTED FINGERPRINT", -100, "RSD", -100, "MANUAL", "abc123"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("two identical manual transactions are allowed: nulls are distinct under the unique index")
    void allowsTwoIdenticalManualTransactions() {
        assertThatCode(() -> {
            jdbc.update(INSERT, LocalDate.of(2026, 11, 3), "KAFA", -180, "RSD", -180, "MANUAL", null);
            jdbc.update(INSERT, LocalDate.of(2026, 11, 3), "KAFA", -180, "RSD", -180, "MANUAL", null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the same source line cannot be imported twice")
    void rejectsDuplicateFingerprint() {
        jdbc.update(INSERT, LocalDate.of(2026, 11, 5), "EPS SNABDEVANJE", -4870, "RSD", -4870, "IMPORT", "fp-eps-1");
        assertThatThrownBy(() -> jdbc.update(INSERT,
                LocalDate.of(2026, 11, 5), "EPS SNABDEVANJE", -4870, "RSD", -4870, "IMPORT", "fp-eps-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an import batch whose counts do not add up is refused")
    void rejectsBatchWhoseCountsDoNotAddUp() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into import_batches
                    (file_name, total_rows, imported_rows, duplicate_rows, rejected_rows)
                values (?, ?, ?, ?, ?)
                """, "wrong.csv", 7, 4, 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
