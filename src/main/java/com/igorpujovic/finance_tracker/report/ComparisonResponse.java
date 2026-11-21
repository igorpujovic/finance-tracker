package com.igorpujovic.finance_tracker.report;

import java.util.List;

/**
 * A month against the one before it. Both months are named in the response, so a
 * January report says out loud that it compared against the previous December rather
 * than leaving the reader to work it out.
 * <p>
 * The previous month is passed in rather than derived here, because the caller has
 * already derived it to build the query and the two must be the same month.
 */
public record ComparisonResponse(
        int year,
        int month,
        int previousYear,
        int previousMonth,
        List<ComparisonRowResponse> categories) {

    static ComparisonResponse of(ReportMonth month, ReportMonth previous,
                                 List<CategoryComparison> rows) {
        return new ComparisonResponse(
                month.year(),
                month.month(),
                previous.year(),
                previous.month(),
                rows.stream().map(ComparisonRowResponse::of).toList());
    }
}
