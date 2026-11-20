package com.igorpujovic.finance_tracker.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A rejected line, kept verbatim alongside the reason it could not become a
 * transaction. The raw line is stored rather than the parsed fields because the
 * parsed fields are exactly what could not be trusted.
 * <p>
 * Duplicates never appear here. Nothing was wrong with a duplicate: it is counted on
 * the batch and skipped.
 */
@Entity
@Table(name = "import_errors")
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Held as a plain identifier rather than an association, as on {@code Transaction}:
     * nothing navigates from an error to its batch, and the batch detail endpoint
     * fetches errors by batch id.
     */
    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    /** The line number in the uploaded file, counting the header as line 1. */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_line", nullable = false, columnDefinition = "text")
    private String rawLine;

    /**
     * A line wrong in several ways carries every reason, joined. They are built from
     * fixed phrases rather than from the values that were wrong — those are already in
     * {@code rawLine} — so the joined reason cannot outgrow this column.
     */
    @Column(nullable = false, length = 255)
    private String reason;

    protected ImportError() {
        // for JPA
    }

    public ImportError(Long importBatchId, RejectedLine rejected) {
        this.importBatchId = importBatchId;
        this.rowNumber = rejected.rowNumber();
        this.rawLine = rejected.rawLine();
        this.reason = rejected.reason();
    }

    public Long getId() {
        return id;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public String getReason() {
        return reason;
    }
}
