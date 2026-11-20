package com.igorpujovic.finance_tracker.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One upload, and the standing record of what became of it. Kept so that an import
 * can be explained months later, which is why the four counts are stored rather than
 * recomputed: deleting a transaction afterwards must not change what the import did.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    protected ImportBatch() {
        // for JPA
    }

    /**
     * Every source line falls into exactly one bucket. The database refuses a batch
     * that says otherwise; this says so one stack frame earlier, where the message can
     * name the four numbers instead of a constraint.
     */
    public ImportBatch(String fileName, int totalRows, int importedRows, int duplicateRows, int rejectedRows) {
        if (totalRows != importedRows + duplicateRows + rejectedRows) {
            throw new IllegalStateException("An import of " + totalRows + " rows cannot report "
                    + importedRows + " imported, " + duplicateRows + " duplicate and "
                    + rejectedRows + " rejected");
        }
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.duplicateRows = duplicateRows;
        this.rejectedRows = rejectedRows;
    }

    @PrePersist
    void stampImportedAt() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }
}
