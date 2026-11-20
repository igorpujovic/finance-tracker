package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.support.PostgresTest;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The issue asks that one transaction write the rows, the errors and the batch summary.
 * Deliberately <strong>not</strong> {@code @Transactional}: every other import test runs
 * inside a transaction of its own, which would wrap the writer and make a writer that
 * opened no transaction at all look identical to one that did. This one commits for
 * real and then asks what survived.
 * <p>
 * The failure it forces is the unique index on {@code transactions.fingerprint} — the
 * backstop ADR-0003 leaves in place and the importer should never reach. Reaching it on
 * purpose, by handing the writer the same source line twice, is the only way to see
 * whether a half-written file can be left behind.
 */
class ImportWriterTest extends PostgresTest {

    @Autowired
    ImportWriter writer;

    @Autowired
    ImportBatchRepository batches;

    @Autowired
    ImportErrorRepository errors;

    @Autowired
    TransactionRepository transactions;

    @AfterEach
    void discardWhateverCommitted() {
        errors.deleteAll();
        transactions.deleteAll();
        batches.deleteAll();
    }

    private static ResolvedLine line(String description) {
        LocalDate date = LocalDate.of(2026, 11, 5);
        BigDecimal amount = new BigDecimal("-4870.00");
        SourceLine source = new SourceLine(2, "05.11.2026," + description + ",-4870.00,RSD",
                date, description, amount, "RSD",
                Fingerprint.of(date, description, amount, "RSD"));
        return new ResolvedLine(source, amount);
    }

    @Test
    @DisplayName("a statement that writes cleanly leaves its batch, its rows and its errors behind")
    void commitsAWholeStatement() {
        ImportBatch batch = writer.write("clean.csv", 2,
                List.of(line("EPS SNABDEVANJE")), 0,
                List.of(new RejectedLine(3, "07.11.2026,LIDL 112,abc,RSD", "the amount is not a number")));

        assertThat(batches.findById(batch.getId())).isPresent();
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(errors.findByImportBatchIdOrderByRowNumberAsc(batch.getId())).hasSize(1);
    }

    @Test
    @DisplayName("a row the database refuses takes the whole import with it, batch summary included")
    void leavesNothingBehindWhenARowIsRefused() {
        ResolvedLine twice = line("EPS SNABDEVANJE");

        assertThatThrownBy(() -> writer.write("duplicated.csv", 2, List.of(twice, twice), 0, List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(batches.count())
                .as("an import batch whose counts describe rows that were never written")
                .isZero();
        assertThat(transactions.count()).isZero();
    }
}
