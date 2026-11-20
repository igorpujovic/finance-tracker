package com.igorpujovic.finance_tracker.imports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One data row of a statement that survived validation, with the fingerprint that
 * identifies it (ADR-0003) and the text it came from kept alongside.
 *
 * @param rowNumber the row's line number in the file, counting the header as line 1
 */
public record SourceLine(
        int rowNumber,
        String rawLine,
        LocalDate date,
        String description,
        BigDecimal amount,
        String currency,
        String fingerprint) {

    public RejectedLine rejectedBecause(String reason) {
        return new RejectedLine(rowNumber, rawLine, reason);
    }
}
