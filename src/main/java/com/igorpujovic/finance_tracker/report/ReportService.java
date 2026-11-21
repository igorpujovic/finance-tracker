package com.igorpujovic.finance_tracker.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Reports are read-only by nature, so the whole service is: nothing here writes, and
 * the transaction says so once rather than per method.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** The most ranked expenses one request can be given, however many it asks for. */
    static final int MAX_LIMIT = 100;

    /** How many it is given when it does not ask. */
    static final int DEFAULT_LIMIT = 10;

    private final ReportRepository reports;

    public ReportService(ReportRepository reports) {
        this.reports = reports;
    }

    public MonthlyReportResponse monthly(ReportMonth month) {
        return MonthlyReportResponse.of(
                month,
                reports.balanceOf(month.start(), month.endExclusive()),
                reports.totalsPerCategory(month.start(), month.endExclusive()));
    }

    /**
     * January against the previous December is not a special case here: it is whatever
     * {@link ReportMonth#previous()} returns.
     */
    public ComparisonResponse comparison(ReportMonth month) {
        ReportMonth previous = month.previous();
        return ComparisonResponse.of(month, previous, reports.comparison(
                month.start(), month.endExclusive(),
                previous.start(), previous.endExclusive()));
    }

    /**
     * A limit past {@link #MAX_LIMIT} is capped rather than refused: asking for too many
     * is a reasonable thing to do, and a cap is what makes this a report. A limit of zero
     * or less is refused instead, because quietly turning it into one would answer a
     * question nobody asked. Neither rule is in the specification, which says only that
     * the limit is "capped at 100" — but both replace a 500 with a 400 that says why.
     */
    public TopExpensesResponse topExpenses(LocalDate from, LocalDate to, int limit) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "The window ends before it starts: from=" + from + ", to=" + to);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("A limit is at least 1; got " + limit);
        }
        int applied = Math.min(limit, MAX_LIMIT);
        return TopExpensesResponse.of(from, to, applied, reports.topExpenses(from, to, applied));
    }
}
