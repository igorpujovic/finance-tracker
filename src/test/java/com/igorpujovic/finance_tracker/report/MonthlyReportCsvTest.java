package com.igorpujovic.finance_tracker.report;

import com.igorpujovic.finance_tracker.category.CategoryType;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export is bytes, and the awkward parts of it are all byte-level: a BOM so that
 * Excel opens a UTF-8 file as UTF-8 instead of as the local code page, CRLF because
 * RFC 4180 says so, and a decimal point that does not become a comma on a machine
 * whose locale is Serbian — in a file whose delimiter is a comma.
 * <p>
 * Tested without a database, because none of that is a query.
 */
class MonthlyReportCsvTest {

    @Test
    @DisplayName("the file opens with a UTF-8 BOM")
    void startsWithABom() {
        assertThat(MonthlyReportCsv.render(september())).startsWith(ExportedCsv.BOM);
    }

    @Test
    @DisplayName("the header is category,type,total_rsd and records end with CRLF")
    void isWrittenInTheRfc4180Dialect() {
        String text = textOf(september());

        assertThat(text).startsWith("category,type,total_rsd\r\n");
        assertThat(text).endsWith("\r\n");
        assertThat(text.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("amounts keep a decimal point and never a thousands separator")
    void writesDotDecimals() {
        assertThat(textOf(september())).contains("Food,EXPENSE,-23450.80");
    }

    @Test
    @DisplayName("the uncategorized row is named, and has no type to name")
    void namesTheUncategorizedBucket() {
        assertThat(textOf(september())).contains("Uncategorized,,-499.99");
    }

    @Test
    @DisplayName("a category name containing the delimiter is quoted")
    void quotesACommaInACategoryName() {
        MonthlyReportResponse report = oneCategory("Food, drink and other");

        assertThat(textOf(report)).contains("\"Food, drink and other\",EXPENSE,-10.00");
    }

    /**
     * The encoding the BOM exists for. A Serbian category name has to survive the round
     * trip as UTF-8, and the second assertion is the one with teeth: decoded as the
     * single-byte charset a spreadsheet would guess without the BOM, the same bytes do
     * <em>not</em> read back as the name — so the file really is multi-byte UTF-8 rather
     * than ASCII that happens to pass.
     */
    @Test
    @DisplayName("a Serbian category name is written as UTF-8, not as the local code page")
    void writesNonAsciiNamesAsUtf8() {
        byte[] file = MonthlyReportCsv.render(oneCategory("Zdravlje i lečenje"));

        assertThat(ExportedCsv.textOf(file)).contains("Zdravlje i lečenje");
        assertThat(ExportedCsv.textOf(file, StandardCharsets.ISO_8859_1))
                .doesNotContain("Zdravlje i lečenje");
    }

    @Test
    @DisplayName("category rows only, with no summary block appended")
    void appendsNoSummary() {
        assertThat(recordsOf(september())).hasSize(3);
        assertThat(textOf(september())).doesNotContainIgnoringCase("balance");
    }

    @Test
    @DisplayName("what is written reads back as the totals that went in")
    void readsBackAsTheSameTotals() {
        BigDecimal total = BigDecimal.ZERO;
        for (CSVRecord record : recordsOf(september())) {
            total = total.add(new BigDecimal(record.get("total_rsd")));
        }

        assertThat(total).isEqualByComparingTo(september().balance());
    }

    /** A month whose rows cover a named expense category, income, and uncategorized money. */
    private static MonthlyReportResponse september() {
        return new MonthlyReportResponse(
                2026, 9,
                new BigDecimal("120000.00"),
                new BigDecimal("-23950.79"),
                new BigDecimal("96049.21"),
                List.of(
                        new CategoryTotalResponse(3L, "Food", CategoryType.EXPENSE, new BigDecimal("-23450.80")),
                        new CategoryTotalResponse(null, null, null, new BigDecimal("-499.99")),
                        new CategoryTotalResponse(1L, "Salary", CategoryType.INCOME, new BigDecimal("120000.00"))));
    }

    private static MonthlyReportResponse oneCategory(String name) {
        BigDecimal ten = new BigDecimal("-10.00");
        return new MonthlyReportResponse(2026, 9, BigDecimal.ZERO, ten, ten,
                List.of(new CategoryTotalResponse(3L, name, CategoryType.EXPENSE, ten)));
    }

    private static String textOf(MonthlyReportResponse report) {
        return ExportedCsv.textOf(MonthlyReportCsv.render(report));
    }

    private static List<CSVRecord> recordsOf(MonthlyReportResponse report) {
        return ExportedCsv.recordsOf(MonthlyReportCsv.render(report));
    }
}
