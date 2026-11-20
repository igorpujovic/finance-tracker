package com.igorpujovic.finance_tracker.rates;

import java.time.LocalDate;

/**
 * The National Bank could not be reached, or answered with something that was not a rate.
 * <p>
 * ADR-0005 reads this as not the statement's fault. Recording it as a rejected line would
 * write a permanent audit record accusing fifty perfectly valid rows of being malformed
 * because the network blinked, in the one table whose whole purpose is to be trusted
 * later; so the upload aborts with 503 and writes nothing at all, and the same file
 * imports cleanly once the network is back.
 */
public class RatesUnavailableException extends RuntimeException {

    RatesUnavailableException(LocalDate date, Throwable cause) {
        super("Exchange rates are unavailable: the rates for " + date
                + " could not be downloaded from the National Bank, so nothing was imported. "
                + "Try the same upload again.", cause);
    }
}
