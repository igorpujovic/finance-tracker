package com.igorpujovic.finance_tracker.imports;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a statement into the lines that can become transactions and the ones that
 * cannot. A rejected line is recorded and the read continues: one bad row in a month
 * of spending is not a reason to refuse the month.
 * <p>
 * A wrong header is the one exception. It means the file is not the thing it claims to
 * be, every row would be rejected for the same reason, and refusing it up front leaves
 * nothing written.
 */
@Component
public class StatementParser {

    /**
     * {@code uuuu} rather than {@code yyyy} because a strict resolver needs a proleptic
     * year, and strict is the point: it is what makes 31.11.2026 a rejected line rather
     * than a silent 30.11.2026.
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    /** The widths of {@code transactions.description} and of {@code numeric(12, 2)}. */
    private static final int DESCRIPTION_LIMIT = 255;
    private static final int AMOUNT_DECIMALS = 2;
    private static final int AMOUNT_WHOLE_DIGITS = 10;

    public ParsedStatement parse(String text) {
        try (CSVParser csv = CSVParser.parse(text, StatementColumns.FORMAT)) {
            requireExpectedHeader(csv.getHeaderNames());

            List<CSVRecord> records = csv.getRecords();
            List<SourceLine> valid = new ArrayList<>();
            List<RejectedLine> rejected = new ArrayList<>();

            // The line number is counted from the text rather than from the row index,
            // so that a blank line or a newline inside a quoted description does not
            // leave every reported number off by one for the rest of the file. A
            // rejected line is meant to be findable in the file it came from.
            int lineNumber = 1;
            int counted = 0;
            for (int i = 0; i < records.size(); i++) {
                int start = firstCharacterOf(text, (int) records.get(i).getCharacterPosition());
                lineNumber += newlinesBetween(text, counted, start);
                counted = start;
                int end = i + 1 < records.size()
                        ? (int) records.get(i + 1).getCharacterPosition()
                        : text.length();
                read(records.get(i), rawLineOf(text, start, end), lineNumber, valid, rejected);
            }
            return new ParsedStatement(records.size(), List.copyOf(valid), List.copyOf(rejected));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A record skipped past a blank line is reported as starting at the line break it
     * skipped, not at its own first character, so the line breaks in between have to be
     * stepped over before they are counted. Only line breaks are stepped over: a leading
     * space belongs to the line, and skipping it would change nothing anyway.
     */
    private int firstCharacterOf(String text, int position) {
        int start = position;
        while (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
            start++;
        }
        return start;
    }

    private int newlinesBetween(String text, int from, int to) {
        int newlines = 0;
        for (int i = from; i < to; i++) {
            if (text.charAt(i) == '\n') {
                newlines++;
            }
        }
        return newlines;
    }

    private void requireExpectedHeader(List<String> header) {
        List<String> found = header.stream().map(String::trim).toList();
        if (!found.equals(StatementColumns.HEADER)) {
            throw new IllegalArgumentException("A statement must start with the header "
                    + StatementColumns.describeHeader() + "; this file starts with "
                    + String.join(",", found));
        }
    }

    /**
     * The raw line is cut out of the original text rather than rebuilt from the parsed
     * fields, so that what a rejected line reports is what the file actually said —
     * quotes, spacing and all — rather than this parser writing it back out its own way.
     */
    private String rawLineOf(String text, int start, int end) {
        return text.substring(start, end).strip();
    }

    private void read(CSVRecord record, String rawLine, int rowNumber,
                      List<SourceLine> valid, List<RejectedLine> rejected) {
        if (record.size() != StatementColumns.HEADER.size()) {
            rejected.add(new RejectedLine(rowNumber, rawLine,
                    "the line has " + record.size() + " columns, not " + StatementColumns.HEADER.size()));
            return;
        }

        List<String> problems = new ArrayList<>();
        LocalDate date = date(record.get(StatementColumns.DATE), problems);
        String description = description(record.get(StatementColumns.DESCRIPTION), problems);
        BigDecimal amount = amount(record.get(StatementColumns.AMOUNT), problems);
        String currency = currency(record.get(StatementColumns.CURRENCY), problems);

        if (!problems.isEmpty()) {
            rejected.add(new RejectedLine(rowNumber, rawLine, String.join("; ", problems)));
            return;
        }
        valid.add(new SourceLine(rowNumber, rawLine, date, description, amount, currency,
                Fingerprint.of(date, description, amount, currency)));
    }

    private LocalDate date(String text, List<String> problems) {
        try {
            return LocalDate.parse(text.strip(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            problems.add("the date is not a real date written as dd.MM.yyyy");
            return null;
        }
    }

    private String description(String text, List<String> problems) {
        String description = text.strip();
        if (description.isEmpty()) {
            problems.add("the description is empty");
            return null;
        }
        if (description.length() > DESCRIPTION_LIMIT) {
            problems.add("the description is longer than " + DESCRIPTION_LIMIT + " characters");
            return null;
        }
        return description;
    }

    /**
     * ADR-0002: the sign of the amount is the only record of income or expense, so a
     * zero amount has no nature and cannot become a transaction. The two size checks
     * refuse an amount the column could not hold, here rather than at the insert, where
     * it would roll back every other line in the file.
     */
    private BigDecimal amount(String text, List<String> problems) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            problems.add("the amount is not a number");
            return null;
        }
        if (amount.signum() == 0) {
            problems.add("the amount is zero, so it is neither income nor an expense");
            return null;
        }
        if (amount.scale() > AMOUNT_DECIMALS) {
            problems.add("the amount has more than " + AMOUNT_DECIMALS + " decimal places");
            return null;
        }
        if (amount.precision() - amount.scale() > AMOUNT_WHOLE_DIGITS) {
            problems.add("the amount is too large to store");
            return null;
        }
        return amount;
    }

    private String currency(String text, List<String> problems) {
        String currency = text.strip().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(currency).matches()) {
            problems.add("the currency is not three letters");
            return null;
        }
        return currency;
    }
}
