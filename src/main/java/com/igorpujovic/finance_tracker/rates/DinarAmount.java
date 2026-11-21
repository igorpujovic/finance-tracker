package com.igorpujovic.finance_tracker.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a transaction is worth in dinars. The whole of the arithmetic, kept apart from
 * every question of where the rate came from.
 */
final class DinarAmount {

    /** The scale of {@code transactions.amount_rsd}, which is {@code numeric(12, 2)}. */
    private static final int DECIMALS = 2;

    /** And its whole-number digits, the twelve of the precision less the two of the scale. */
    private static final int WHOLE_DIGITS = 10;

    private DinarAmount() {
    }

    /**
     * ADR-0002: this multiplies and never takes a magnitude. A converter that returned
     * absolute values would turn every foreign expense into income, and the only visible
     * symptom would be a balance wrong by twice the foreign spend. {@code HALF_UP} rounds
     * away from zero on both sides of it, so a credit and the debit that reverses it round
     * to the same size.
     *
     * @param rateToRsd what one unit of the currency is worth in dinars
     */
    static BigDecimal of(BigDecimal amount, BigDecimal rateToRsd) {
        return amount.multiply(rateToRsd).setScale(DECIMALS, RoundingMode.HALF_UP);
    }

    /**
     * Whether {@code transactions.amount_rsd} could hold this. Asked before the insert
     * rather than at it: on the import path an overflow caught here costs its own line
     * where caught at the insert it would roll back every other line in the file, and on
     * the manual path it is the difference between a reason naming the amount and a
     * {@code DataIntegrityViolationException}.
     */
    static boolean fitsTheColumn(BigDecimal dinars) {
        return dinars.precision() - dinars.scale() <= WHOLE_DIGITS;
    }
}
