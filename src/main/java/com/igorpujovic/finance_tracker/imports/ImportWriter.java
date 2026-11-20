package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.transaction.Transaction;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
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

    ImportWriter(ImportBatchRepository batches, ImportErrorRepository errors,
                 TransactionRepository transactions) {
        this.batches = batches;
        this.errors = errors;
        this.transactions = transactions;
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

        for (ResolvedLine resolved : imported) {
            SourceLine line = resolved.line();
            transactions.save(Transaction.imported(
                    line.date(),
                    line.description(),
                    line.amount(),
                    line.currency(),
                    resolved.amountRsd(),
                    // Step 3 of the roadmap matches a rule here. Until then an imported
                    // transaction arrives uncategorized, which is a real state with its
                    // own filter, not a gap.
                    null,
                    batch.getId(),
                    line.fingerprint()));
        }

        for (RejectedLine line : rejected) {
            errors.save(new ImportError(batch.getId(), line));
        }
        return batch;
    }
}
