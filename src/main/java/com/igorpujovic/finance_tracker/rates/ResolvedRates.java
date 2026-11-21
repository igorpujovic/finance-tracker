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
     * What an amount is worth in dinars, given the currency it was spent in and the day
     * it was spent on — or the reason it is worth nothing storable.
     * <p>
     * One amount, whether it came from a source line of a statement or from a request
     * carrying a single transaction. Both read the three outcomes of ADR-0005 the same way
     * because both get them from here: a currency the bank does not publish, a conversion
     * the column cannot hold, and the conversion itself. The fourth outcome — the bank
     * unreachable — never reaches this object, which is the point of resolving first.
     */
    public Conversion conversionOf(BigDecimal amount, CurrencyOnDate spent) {
        return dinarAmountOf(amount, spent)
                .map(dinars -> storable(amount, spent, dinars))
                .orElseGet(() -> new Conversion.Unavailable(
                        "no exchange rate for " + spent.currency() + " on " + spent.date()));
    }

    /**
     * A dinar amount short-circuits: it is worth itself, with no lookup and no stored row
     * to get wrong.
     *
     * @return empty when the National Bank publishes no rate for that currency on or
     *         before that date, which ADR-0005 reads as the line's own problem rather
     *         than the network's
     */
    private Optional<BigDecimal> dinarAmountOf(BigDecimal amount, CurrencyOnDate spent) {
        if (spent.isDinar()) {
            return Optional.of(amount);
        }
        return Optional.ofNullable(rates.get(spent)).map(rate -> DinarAmount.of(amount, rate));
    }

    /**
     * Both readings of "the column could not hold it" are the amount's own problem and
     * neither is reachable from a sane rate, which is why they each name the amount, its
     * currency and its date rather than only the dinars they came to.
     */
    private Conversion storable(BigDecimal amount, CurrencyOnDate spent, BigDecimal dinars) {
        String which = amount + " " + spent.currency() + " on " + spent.date();
        if (dinars.signum() == 0) {
            // ADR-0002: zero has no sign and therefore no nature, so it cannot be stored
            // even though the amount asked about was a real expense or a real income.
            return new Conversion.Unavailable(which + " is worth less than 0.01 RSD");
        }
        if (!DinarAmount.fitsTheColumn(dinars)) {
            return new Conversion.Unavailable(which + " is worth more dinars than can be stored");
        }
        return new Conversion.DinarAmount(dinars);
    }
}
