package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.rule.Categorization;
import com.igorpujovic.finance_tracker.rule.RuleService;
import com.igorpujovic.finance_tracker.transaction.Transaction;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import com.igorpujovic.finance_tracker.transaction.TransactionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only part of an import that touches the database for writing, and it does so
 * once: the valid rows, the rejected lines and the batch summary are one transaction,
 * so a file is never half imported.
 * <p>
 * It is a separate bean from {@link ImportService} rather than a method on it so that
 * the transaction boundary is a boundary between objects. ADR-0005 requires parsing
 * and rate resolution to finish before this opens, and a self-call on one bean would
 * not have opened a transaction here at all.
 */
@Component
class ImportWriter {

    private final ImportBatchRepository batches;
    private final ImportErrorRepository errors;
    private final TransactionRepository transactions;
    private final RuleService rules;

    ImportWriter(ImportBatchRepository batches, ImportErrorRepository errors,
                 TransactionRepository transactions, RuleService rules) {
        this.batches = batches;
        this.errors = errors;
        this.transactions = transactions;
        this.rules = rules;
    }

    /**
     * Public although the class is not, because that is the visibility Spring documents
     * for {@code @Transactional}. A CGLIB proxy does advise a package-private method —
     * {@code ImportWriterTest} commits for real and proves the rollback — but only
     * because the generated subclass lands in this package, so the guarantee would
     * disappear with a switch to JDK dynamic proxies and take no test with it.
     */
    @Transactional
    public ImportBatch write(String fileName, int totalRows, List<ResolvedLine> imported,
                             int duplicateRows, List<RejectedLine> rejected) {
        ImportBatch batch = batches.save(new ImportBatch(
                fileName, totalRows, imported.size(), duplicateRows, rejected.size()));

        // Read once for the whole statement, not once per line. The rules are a local
        // query rather than the kind of call ADR-0005 keeps outside this transaction:
        // what that ordering exists to prevent is an HTTP request made while a database
        // transaction is held, and a rule set is neither slow nor able to fail halfway.
        Categorization categorization = rules.currentCategorization();

        for (ResolvedLine resolved : imported) {
            SourceLine line = resolved.line();
            transactions.save(Transaction.imported(
                    line.date(),
                    line.description(),
                    line.amount(),
                    line.currency(),
                    resolved.amountRsd(),
                    // ADR-0004: the rules that cannot legally take this line are not
                    // candidates, so a line nothing compatible claims is written
                    // uncategorized — a real state with its own filter, not a gap.
                    categorization
                            .categoryFor(line.description(), TransactionType.of(line.amount()))
                            .orElse(null),
                    batch.getId(),
                    line.fingerprint()));
        }

        for (RejectedLine line : rejected) {
            errors.save(new ImportError(batch.getId(), line));
        }
        return batch;
    }
}
