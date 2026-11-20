package com.igorpujovic.finance_tracker.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * One rate as the Kurs API published it, already reduced to what this project keeps: the
 * value of a single unit, on the date the publication is attributed to, at the scale the
 * column holds.
 */
record PublishedRate(String currency, LocalDate rateDate, BigDecimal rateToRsd) {

    /**
     * The API quotes {@code exchange_middle} for {@code parity} units — a hundred of them
     * for the currencies whose single unit is worth a fraction of a dinar — so the rate
     * this project stores is the quotient.
     * <p>
     * It is rounded to the column's scale here rather than kept exact on purpose. A line
     * converted now from this quotient and the same line converted later from the stored
     * row have to come to the same dinar amount, and they only do if both multiply by the
     * number the column can hold.
     */
    static PublishedRate of(String currency, LocalDate rateDate, BigDecimal exchangeMiddle, int parity) {
        return new PublishedRate(currency, rateDate,
                exchangeMiddle.divide(BigDecimal.valueOf(parity), ExchangeRate.DECIMALS, RoundingMode.HALF_UP));
    }
}
