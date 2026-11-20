package com.igorpujovic.finance_tracker.imports;

import java.math.BigDecimal;

/**
 * A source line that will be written, paired with the dinar amount resolved for it.
 * Named for ADR-0005, where the rates a statement needs are resolved before the import
 * transaction opens: this is one line on the far side of that pre-pass.
 */
record ResolvedLine(SourceLine line, BigDecimal amountRsd) {
}
