package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.rates.CurrencyOnDate;
import com.igorpujovic.finance_tracker.rates.ExchangeRateService;
import com.igorpujovic.finance_tracker.rates.ResolvedRates;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The import's side of the rate pre-pass of ADR-0005: the dinar amount of every line a
 * statement is about to write, resolved before any database transaction opens, so that no
 * HTTP call is ever made while one is held.
 * <p>
 * It hands the whole set of currency-and-date pairs over at once rather than asking line
 * by line. That is the entire reason the pre-pass exists: resolving them together costs
 * one download per genuinely missing date, where asking per line would cost one per row
 * of a bank statement.
 * <p>
 * What comes back is one {@link Conversion} per line, no partial state and nothing to
 * undo — and a line nothing can convert is the line's own problem. A failure to reach the
 * National Bank at all is not, and does not arrive here: it leaves
 * {@link ExchangeRateService} as an exception the whole upload aborts on.
 */
@Component
class StatementConversion {

    /** The widths of {@code transactions.amount_rsd}, which is {@code numeric(12, 2)}. */
    private static final int DINAR_WHOLE_DIGITS = 10;

    private final ExchangeRateService rates;

    StatementConversion(ExchangeRateService rates) {
        this.rates = rates;
    }

    Map<Integer, Conversion> convert(List<SourceLine> lines) {
        Map<Integer, CurrencyOnDate> spent = new HashMap<>();
        for (SourceLine line : lines) {
            spent.put(line.rowNumber(), new CurrencyOnDate(line.currency(), line.date()));
        }
        ResolvedRates resolved = rates.resolve(spent.values());

        Map<Integer, Conversion> conversions = new HashMap<>();
        for (SourceLine line : lines) {
            conversions.put(line.rowNumber(),
                    conversionOf(line, spent.get(line.rowNumber()), resolved));
        }
        return conversions;
    }

    private Conversion conversionOf(SourceLine line, CurrencyOnDate spent, ResolvedRates resolved) {
        return resolved.dinarAmountOf(line.amount(), spent)
                .map(dinars -> storable(line, dinars))
                .orElseGet(() -> new Conversion.Unavailable(
                        "no exchange rate for " + spent.currency() + " on " + spent.date()));
    }

    /**
     * A conversion the column could not hold is refused here rather than at the insert,
     * where it would roll back every other line in the file — the same reason the parser
     * checks the size of the amount the file gave. Both readings of "could not hold" are
     * the line's own problem and neither is reachable from a sane rate, which is why they
     * each name the line's own amount, currency and date.
     */
    private Conversion storable(SourceLine line, BigDecimal dinars) {
        String which = line.amount() + " " + line.currency() + " on " + line.date();
        if (dinars.signum() == 0) {
            // ADR-0002: zero has no sign and therefore no nature, so it cannot be stored
            // even though the amount the file gave was a real expense or a real income.
            return new Conversion.Unavailable(which + " is worth less than 0.01 RSD");
        }
        if (dinars.precision() - dinars.scale() > DINAR_WHOLE_DIGITS) {
            return new Conversion.Unavailable(which + " is worth more dinars than can be stored");
        }
        return new Conversion.DinarAmount(dinars);
    }
}
