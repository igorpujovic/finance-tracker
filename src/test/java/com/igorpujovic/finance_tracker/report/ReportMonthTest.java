package com.igorpujovic.finance_tracker.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The calendar arithmetic the reports rest on, pinned where it is cheap to read: the
 * window a month spans, the month before it, and the name its export carries.
 * <p>
 * The window is half-open, which is why no query in {@link ReportRepository} knows how
 * long a month is — including February's, in a leap year or out of one.
 */
class ReportMonthTest {

    @Test
    @DisplayName("the window runs from the first of the month to the first of the next")
    void spansTheMonthHalfOpen() {
        ReportMonth september = ReportMonth.of(2026, 9);

        assertThat(september.start()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(september.endExclusive()).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    @Test
    @DisplayName("February is 28 days or 29 without anything having to count them")
    void handlesFebruary() {
        assertThat(ReportMonth.of(2027, 2).endExclusive()).isEqualTo(LocalDate.parse("2027-03-01"));
        assertThat(ReportMonth.of(2028, 2).endExclusive()).isEqualTo(LocalDate.parse("2028-03-01"));
        assertThat(ReportMonth.of(2028, 2).start().plusDays(28))
                .isEqualTo(LocalDate.parse("2028-02-29"));
    }

    @Test
    @DisplayName("the month before January is the previous December")
    void stepsBackAcrossTheYear() {
        ReportMonth previous = ReportMonth.of(2027, 1).previous();

        assertThat(previous.year()).isEqualTo(2026);
        assertThat(previous.month()).isEqualTo(12);
        assertThat(previous.start()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(previous.endExclusive()).isEqualTo(LocalDate.parse("2027-01-01"));
    }

    @Test
    @DisplayName("the export file name is zero-padded, so a year of them sorts")
    void namesTheExportFile() {
        assertThat(ReportMonth.of(2026, 9).exportFileName()).isEqualTo("monthly-report-2026-09.csv");
        assertThat(ReportMonth.of(2026, 12).exportFileName()).isEqualTo("monthly-report-2026-12.csv");
    }

    @Test
    @DisplayName("a month outside 1 to 12 is refused with a reason, not a DateTimeException")
    void refusesAMonthThatIsNotOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReportMonth.of(2026, 0))
                .withMessageContaining("1 to 12");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReportMonth.of(2026, 13))
                .withMessageContaining("1 to 12");
    }

    @Test
    @DisplayName("a year no statement could belong to is refused")
    void refusesAnImplausibleYear() {
        assertThatIllegalArgumentException().isThrownBy(() -> ReportMonth.of(0, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> ReportMonth.of(10_000, 9));
    }
}
