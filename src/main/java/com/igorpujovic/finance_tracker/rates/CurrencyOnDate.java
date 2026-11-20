package com.igorpujovic.finance_tracker.rates;

import java.time.LocalDate;

/**
 * A currency and the date something happened in it: the pair a rate is looked up by, and
 * the unit the pre-pass of ADR-0005 counts in. A statement needing twenty euro lines
 * across four days needs four of these, which is why the pre-pass reaches for the network
 * once per date rather than once per line.
 */
public record CurrencyOnDate(String currency, LocalDate date) {

    /** The currency everything is reported in, and the one nobody has to look up. */
    public static final String DINAR = "RSD";

    public boolean isDinar() {
        return DINAR.equals(currency);
    }
}
