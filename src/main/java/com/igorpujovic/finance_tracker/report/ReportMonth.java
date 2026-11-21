package com.igorpujovic.finance_tracker.report;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The month a report is about, and the three things every report derives from it: the
 * half-open window its queries filter on, the month before it, and the name its export
 * file carries.
 * <p>
 * It exists so that nothing else in this package does calendar arithmetic. January
 * comparing against the previous December is not a special case anywhere — it is what
 * {@link #previous()} returns — and the window is half-open so that no query has to
 * know how long a month is.
 */
public record ReportMonth(YearMonth yearMonth) {

    /** Wide enough for any statement anyone holds, and narrow enough that {@code %04d} is a year. */
    private static final int EARLIEST_YEAR = 1900;
    private static final int LATEST_YEAR = 9999;

    /**
     * Rejects what {@code YearMonth.of} would reject anyway, but as an
     * {@link IllegalArgumentException} — a 400 with a reason, rather than the
     * {@code DateTimeException} that reaches no handler and answers 500.
     */
    public static ReportMonth of(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("A month is 1 to 12; got " + month);
        }
        if (year < EARLIEST_YEAR || year > LATEST_YEAR) {
            throw new IllegalArgumentException(
                    "A year is " + EARLIEST_YEAR + " to " + LATEST_YEAR + "; got " + year);
        }
        return new ReportMonth(YearMonth.of(year, month));
    }

    /** The first day of the month, included in the window. */
    public LocalDate start() {
        return yearMonth.atDay(1);
    }

    /**
     * The first day of the next month, excluded from the window. Half-open rather than
     * {@code <= } the last day, so that a month's length is Postgres's problem and not
     * a query's.
     */
    public LocalDate endExclusive() {
        return yearMonth.plusMonths(1).atDay(1);
    }

    public ReportMonth previous() {
        return new ReportMonth(yearMonth.minusMonths(1));
    }

    public int year() {
        return yearMonth.getYear();
    }

    public int month() {
        return yearMonth.getMonthValue();
    }

    /** {@code monthly-report-2026-09.csv} — zero-padded, so the files of one year sort. */
    public String exportFileName() {
        return "monthly-report-%04d-%02d.csv".formatted(year(), month());
    }
}
