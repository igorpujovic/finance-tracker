package com.igorpujovic.finance_tracker.imports;

/**
 * A source line that could not become a transaction, kept verbatim with the reason.
 * One rejected line never stops an import.
 *
 * @param rowNumber the row's line number in the file, counting the header as line 1
 */
public record RejectedLine(int rowNumber, String rawLine, String reason) {
}
