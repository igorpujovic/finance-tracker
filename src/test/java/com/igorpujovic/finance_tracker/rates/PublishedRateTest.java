package com.igorpujovic.finance_tracker.rates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning what the Kurs API quotes into the rate this project stores: {@code
 * exchange_middle / parity}, at the scale the column holds. Pure arithmetic, so pure
 * JUnit per ADR-0006.
 */
class PublishedRateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 19);

    private static BigDecimal rateOf(String exchangeMiddle, int parity) {
        return PublishedRate.of("XXX", DATE, new BigDecimal(exchangeMiddle), parity).rateToRsd();
    }

    @Test
    @DisplayName("a currency quoted per single unit is stored as quoted")
    void keepsAQuotePerUnitAsItIs() {
        assertThat(rateOf("117.2061", 1)).isEqualByComparingTo("117.2061");
    }

    @Test
    @DisplayName("a currency quoted per hundred units is stored per unit")
    void dividesAQuotePerHundredUnits() {
        // The live API's own figures for the Belgian franc on 2026-11-19: exchange_middle
        // 291.6243 for a parity of 100. Stored as quoted, every franc line would be worth
        // a hundred times too much and look entirely plausible.
        assertThat(rateOf("291.6243", 100)).isEqualByComparingTo("2.9162");
    }

    @Test
    @DisplayName("the quotient is carried to the four decimals the column holds")
    void carriesFourDecimalPlaces() {
        // 76.1250 / 100 is 0.76125, which is exactly half of the last place the column
        // keeps, so this pins the rounding as well as the scale.
        assertThat(rateOf("76.1250", 100)).isEqualByComparingTo("0.7613");
        assertThat(rateOf("117.2061", 1).scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("the quotient is rounded before it is multiplied, not after")
    void roundsTheRateBeforeConverting() {
        // The decision this class exists to make. A thousand francs at the stored 2.9162
        // is 2916.20; at the exact quotient 2.916243 it would be 2916.24. Converting a
        // line now and converting it again later from the stored row have to agree, and
        // they only do if both multiply by the number the column can hold.
        BigDecimal stored = rateOf("291.6243", 100);

        assertThat(DinarAmount.of(new BigDecimal("1000.00"), stored))
                .isEqualByComparingTo("2916.20")
                .isNotEqualByComparingTo("2916.24");
    }
}
