package com.igorpujovic.finance_tracker.rates;

import java.math.BigDecimal;

/**
 * What the rate pre-pass concluded about one amount: either the dinar amount it is
 * worth, or the reason it has none.
 * <p>
 * ADR-0005 reads a missing rate as the fault of the amount that needed it, never the
 * network: a transport failure never arrives here at all, but as
 * {@link RatesUnavailableException}. So an import rejects the one source line and writes
 * the rest, where it aborts the whole upload on the exception; and a request carrying a
 * single transaction answers 400 with the reason here, where it answers 503 there.
 */
public sealed interface Conversion {

    record DinarAmount(BigDecimal value) implements Conversion {
    }

    record Unavailable(String reason) implements Conversion {
    }
}
