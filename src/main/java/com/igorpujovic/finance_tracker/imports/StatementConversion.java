package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.rates.Conversion;
import com.igorpujovic.finance_tracker.rates.CurrencyOnDate;
import com.igorpujovic.finance_tracker.rates.ExchangeRateService;
import com.igorpujovic.finance_tracker.rates.ResolvedRates;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The import's side of the rate pre-pass of ADR-0005: the dinar amount of every line a
 * statement is about to write, resolved before any database transaction opens, so that no
 * HTTP call is ever made while one is held.
 * <p>
 * It hands the whole set of currency-and-date pairs over at once rather than asking line
 * by line, and that is the entire reason it exists as a class of its own: resolving them
 * together costs one download per genuinely missing date, where asking per line would
 * cost one per row of a bank statement. What each line is then worth is
 * {@link ResolvedRates#conversionOf}, which the manual endpoint asks the same question
 * of, one line at a time.
 * <p>
 * What comes back is one {@link Conversion} per line, no partial state and nothing to
 * undo — and a line nothing can convert is the line's own problem. A failure to reach the
 * National Bank at all is not, and does not arrive here: it leaves
 * {@link ExchangeRateService} as an exception the whole upload aborts on.
 */
@Component
class StatementConversion {

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
                    resolved.conversionOf(line.amount(), spent.get(line.rowNumber())));
        }
        return conversions;
    }
}
