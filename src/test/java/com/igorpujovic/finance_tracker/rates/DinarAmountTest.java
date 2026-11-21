package com.igorpujovic.finance_tracker.rates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conversion arithmetic, which ADR-0006 keeps in pure JUnit: there is no query and no
 * constraint in it, so a container would add nothing but seconds.
 * <p>
 * Every expected value here is a worked multiplication, not a second implementation of
 * the one under test.
 */
class DinarAmountTest {

    private static final BigDecimal EURO = new BigDecimal("117.2061");

    @Test
    @DisplayName("an expense stays an expense: the sign survives conversion")
    void keepsTheSignOfAnExpense() {
        // ADR-0002: a converter that returned magnitudes would turn every foreign expense
        // into income, and the only visible symptom would be a balance wrong by twice the
        // foreign spend. -9.99 x 117.2061 is -1170.888939.
        assertThat(DinarAmount.of(new BigDecimal("-9.99"), EURO))
                .isEqualByComparingTo("-1170.89");
    }

    @Test
    @DisplayName("income stays income")
    void keepsTheSignOfIncome() {
        // 100.00 x 117.2061 is 11720.61 exactly.
        assertThat(DinarAmount.of(new BigDecimal("100.00"), EURO))
                .isEqualByComparingTo("11720.61");
    }

    @Test
    @DisplayName("a half-dinar rounds away from zero on both sides of it")
    void roundsAHalfAwayFromZero() {
        // 2.3450 is exactly half a hundredth, so the rounding mode is visible rather than
        // inferred: a refund and the expense it reverses have to round to the same size,
        // or the pair would not cancel in a balance.
        assertThat(DinarAmount.of(new BigDecimal("1.00"), new BigDecimal("2.3450")))
                .isEqualByComparingTo("2.35");
        assertThat(DinarAmount.of(new BigDecimal("-1.00"), new BigDecimal("2.3450")))
                .isEqualByComparingTo("-2.35");
    }

    @Test
    @DisplayName("the widest amount the dinar column holds fits, and one digit more does not")
    void knowsWhatTheDinarColumnHolds() {
        // `numeric(12, 2)` is ten whole digits and two decimals, so the boundary is
        // asserted on both sides of itself: a converter that read the precision as twelve
        // whole digits would pass everything here and fail at the insert, where on the
        // import path it would roll back every other line of the file.
        assertThat(DinarAmount.fitsTheColumn(new BigDecimal("9999999999.99"))).isTrue();
        assertThat(DinarAmount.fitsTheColumn(new BigDecimal("-9999999999.99"))).isTrue();
        assertThat(DinarAmount.fitsTheColumn(new BigDecimal("10000000000.00"))).isFalse();
        assertThat(DinarAmount.fitsTheColumn(new BigDecimal("-10000000000.00"))).isFalse();
    }

    @Test
    @DisplayName("the dinar amount is carried to exactly two decimal places")
    void carriesTwoDecimalPlaces() {
        // The scale of `transactions.amount_rsd`. Asserted as scale rather than by value,
        // because `isEqualByComparingTo` above would pass on an unrounded 1170.888939.
        assertThat(DinarAmount.of(new BigDecimal("-9.99"), EURO).scale()).isEqualTo(2);
    }
}
