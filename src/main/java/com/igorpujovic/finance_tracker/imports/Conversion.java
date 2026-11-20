package com.igorpujovic.finance_tracker.imports;

import java.math.BigDecimal;

/**
 * What the rate pre-pass concluded about one source line: either the dinar amount it
 * is worth, or the reason it has none.
 * <p>
 * ADR-0005 reads a missing rate as the line being at fault, never the network: a
 * transport failure aborts the whole upload instead of writing a permanent audit
 * record accusing valid rows of being malformed.
 */
public sealed interface Conversion {

    record DinarAmount(BigDecimal value) implements Conversion {
    }

    record Unavailable(String reason) implements Conversion {
    }
}
