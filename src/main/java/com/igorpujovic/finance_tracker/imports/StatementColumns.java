package com.igorpujovic.finance_tracker.imports;

import org.apache.commons.csv.CSVFormat;

import java.util.List;

/**
 * The shape of a statement file: which columns it must name, in which order, and the
 * CSV dialect they are written in. Everything that knows a bank's column names knows
 * it from here, so adopting a second bank's layout is a change to one class.
 */
final class StatementColumns {

    static final String DATE = "date";
    static final String DESCRIPTION = "description";
    static final String AMOUNT = "amount";
    static final String CURRENCY = "currency";

    static final List<String> HEADER = List.of(DATE, DESCRIPTION, AMOUNT, CURRENCY);

    /**
     * The header is read from the file rather than declared, so that it can be compared
     * with {@link #HEADER} and a wrong one refused instead of silently read as data.
     * Surrounding spaces are dropped so that {@code 01.11.2026, PLATA} parses, while
     * quotes still protect a comma inside a description.
     */
    static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setIgnoreEmptyLines(true)
            .get();

    private StatementColumns() {
    }

    static String describeHeader() {
        return String.join(",", HEADER);
    }
}
