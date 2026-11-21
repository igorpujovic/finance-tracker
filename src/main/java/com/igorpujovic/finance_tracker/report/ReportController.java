package com.igorpujovic.finance_tracker.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The read side of the project: the ways of asking what a month came to.
 * <p>
 * Every figure these return is signed (ADR-0002). An expense is a negative number here
 * as it is everywhere else, and a positive change in the comparison always means the
 * position improved, for an expense category as much as for an income one.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    public MonthlyReportResponse monthly(@RequestParam int year, @RequestParam int month) {
        return service.monthly(ReportMonth.of(year, month));
    }

    /** The month named, against the one before it — which for January is last December. */
    @GetMapping("/comparison")
    public ComparisonResponse comparison(@RequestParam int year, @RequestParam int month) {
        return service.comparison(ReportMonth.of(year, month));
    }

    /**
     * The window is required and inclusive at both ends. Unlike the transaction listing,
     * whose filters are optional because its default is "everything, paged", a ranked
     * top-N over all of history is not a question this endpoint is for.
     */
    @GetMapping("/top-expenses")
    public TopExpensesResponse topExpenses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "" + ReportService.DEFAULT_LIMIT) int limit) {
        return service.topExpenses(from, to, limit);
    }

    /**
     * The same monthly report as a file. It is assembled from the response the JSON
     * endpoint returns, not from a second query, so the two cannot disagree about a
     * month: the only difference between them is the rendering.
     * <p>
     * {@code byte[]} rather than a String, because the body carries a byte-order mark
     * that no character-level writer should be able to re-encode or drop.
     */
    @GetMapping(value = "/monthly/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportMonthly(@RequestParam int year, @RequestParam int month) {
        ReportMonth reportMonth = ReportMonth.of(year, month);
        byte[] file = MonthlyReportCsv.render(service.monthly(reportMonth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reportMonth.exportFileName())
                        .build()
                        .toString())
                .body(file);
    }
}
