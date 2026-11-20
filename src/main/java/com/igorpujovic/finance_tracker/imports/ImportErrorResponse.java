package com.igorpujovic.finance_tracker.imports;

/**
 * A rejected line as the API reports it: where it was, what it said, and why it could
 * not become a transaction.
 *
 * @param rowNumber the line number in the uploaded file, counting the header as line 1
 */
public record ImportErrorResponse(int rowNumber, String rawLine, String reason) {

    public static ImportErrorResponse of(ImportError error) {
        return new ImportErrorResponse(error.getRowNumber(), error.getRawLine(), error.getReason());
    }
}
