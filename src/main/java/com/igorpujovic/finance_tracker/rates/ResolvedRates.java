package com.igorpujovic.finance_tracker.rates;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * The rates a batch of lines needed, on the far side of the pre-pass of ADR-0005: every
 * lookup already done, every genuine gap already downloaded, and nothing left that can
 * reach the network. Holding the answers in one object is what lets the import open a
 * transaction and convert without going back out.
 */
public final class ResolvedRates {

    private final Map<CurrencyOnDate, BigDecimal> rates;

    ResolvedRates(Map<CurrencyOnDate, BigDecimal> rates) {
        this.rates = Map.copyOf(rates);
    }

    /**
     * What an amount is worth in dinars, given the currency it was spent in and the day it
     * was spent on.
     * <p>
     * A dinar amount short-circuits: it is worth itself, with no lookup and no stored row
     * to get wrong.
     *
     * @return empty when the National Bank publishes no rate for that currency on or
     *         before that date, which ADR-0005 reads as the line's own problem rather
     *         than the network's
     */
    public Optional<BigDecimal> dinarAmountOf(BigDecimal amount, CurrencyOnDate spent) {
        if (spent.isDinar()) {
            return Optional.of(amount);
        }
        return Optional.ofNullable(rates.get(spent)).map(rate -> DinarAmount.of(amount, rate));
    }
}
