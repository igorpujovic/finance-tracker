package com.igorpujovic.finance_tracker.imports;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * An import batch as the API reports it. {@code errors} is present only on the detail
 * endpoint: a list of a few hundred past imports should not carry every rejected line
 * of every one of them, and an absent field says that more plainly than an empty list,
 * which would claim the import rejected nothing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportBatchResponse(
        Long id,
        String fileName,
        Instant importedAt,
        int totalRows,
        int importedRows,
        int duplicateRows,
        int rejectedRows,
        List<ImportErrorResponse> errors) {

    public static ImportBatchResponse of(ImportBatch batch) {
        return withErrors(batch, null);
    }

    public static ImportBatchResponse withErrors(ImportBatch batch, List<ImportError> errors) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getImportedAt(),
                batch.getTotalRows(),
                batch.getImportedRows(),
                batch.getDuplicateRows(),
                batch.getRejectedRows(),
                errors == null ? null : errors.stream().map(ImportErrorResponse::of).toList());
    }
}
