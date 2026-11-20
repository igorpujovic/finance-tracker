package com.igorpujovic.finance_tracker.imports;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rate pre-pass of ADR-0005: the dinar amount of every line a statement is about
 * to write, resolved before any database transaction opens, so that no HTTP call is
 * ever made while one is held.
 * <p>
 * Step 4 of the roadmap fills this in — collecting the distinct currency-and-date
 * pairs the lines need, reading the stored rates and fetching the genuine gaps from
 * the Kurs API. Until then a dinar line converts at one and a foreign line is the only
 * kind that can fail, which is exactly the shape the real pre-pass has: one
 * {@link Conversion} per line, no partial state, nothing to undo.
 */
@Component
class StatementConversion {

    private static final String DINAR = "RSD";

    Map<Integer, Conversion> convert(List<SourceLine> lines) {
        Map<Integer, Conversion> conversions = new HashMap<>();
        for (SourceLine line : lines) {
            conversions.put(line.rowNumber(), DINAR.equals(line.currency())
                    ? new Conversion.DinarAmount(line.amount())
                    : new Conversion.Unavailable(
                            "no exchange rate for " + line.currency() + " on " + line.date()));
        }
        return conversions;
    }
}
