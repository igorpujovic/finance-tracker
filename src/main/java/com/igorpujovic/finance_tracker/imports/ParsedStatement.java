package com.igorpujovic.finance_tracker.imports;

import java.util.List;

/**
 * What reading a statement produced: every data row sorted into the ones that can
 * become transactions and the ones that cannot. {@code totalRows} is the number of
 * data rows the file held, so it always equals the two lists' sizes added together.
 */
public record ParsedStatement(int totalRows, List<SourceLine> valid, List<RejectedLine> rejected) {
}
