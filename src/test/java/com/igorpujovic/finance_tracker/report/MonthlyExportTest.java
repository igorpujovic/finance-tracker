package com.igorpujovic.finance_tracker.report;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The export against the JSON it exports. Two renderings of one month have to agree, and
 * the only way to know they do is to read the file back with a parser rather than to
 * compare it with a string the test wrote itself.
 */
class MonthlyExportTest extends ReportTest {

    @BeforeEach
    void aSeptemberWorthOfSpending() {
        saveSeptember();
    }

    @Test
    @DisplayName("the exported file is named for the month it covers")
    void isNamedForItsMonth() throws Exception {
        mvc.perform(get("/api/reports/monthly/export?year=2026&month=9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"monthly-report-2026-09.csv\""))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
    }

    @Test
    @DisplayName("the exported totals are the totals the JSON endpoint reports for the same month")
    void agreesWithTheJsonEndpoint() throws Exception {
        JsonNode report = getJson("/api/reports/monthly?year=2026&month=9");

        Map<String, BigDecimal> fromJson = new LinkedHashMap<>();
        for (JsonNode row : report.get("categories")) {
            JsonNode name = row.get("categoryName");
            fromJson.put(name.isNull() ? MonthlyReportCsv.UNCATEGORIZED : name.asString(),
                    row.get("totalRsd").decimalValue());
        }

        Map<String, BigDecimal> fromCsv = new LinkedHashMap<>();
        BigDecimal exported = BigDecimal.ZERO;
        for (CSVRecord record : exportOfSeptember()) {
            BigDecimal total = new BigDecimal(record.get("total_rsd"));
            fromCsv.put(record.get("category"), total);
            exported = exported.add(total);
        }

        // Compared row by row rather than with containsExactlyEntriesOf, because
        // BigDecimal.equals is scale-sensitive and the JSON reader strips the trailing
        // zero off -4870.00. The order of the rows still has to agree.
        assertThat(fromCsv.keySet()).containsExactlyElementsOf(fromJson.keySet());
        fromJson.forEach((category, total) ->
                assertThat(fromCsv.get(category)).isEqualByComparingTo(total));

        assertThat(exported).isEqualByComparingTo(report.get("balance").decimalValue());
    }

    @Test
    @DisplayName("each exported row carries the category's type, and the uncategorized row none")
    void carriesTheCategoryType() throws Exception {
        Map<String, String> types = new LinkedHashMap<>();
        for (CSVRecord record : exportOfSeptember()) {
            types.put(record.get("category"), record.get("type"));
        }

        assertThat(types).containsEntry("Food", "EXPENSE");
        assertThat(types).containsEntry("Salary", "INCOME");
        assertThat(types).containsEntry(MonthlyReportCsv.UNCATEGORIZED, "");
    }

    /**
     * The BOM survives the trip through the message converter, which is the part a unit
     * test of the renderer cannot show: a response written as characters rather than
     * bytes would re-encode or drop it.
     */
    @Test
    @DisplayName("the file arrives over HTTP with its byte-order mark intact")
    void isUtf8WithABom() throws Exception {
        assertThat(exportBytes()).startsWith(ExportedCsv.BOM);
    }

    private List<CSVRecord> exportOfSeptember() throws Exception {
        return ExportedCsv.recordsOf(exportBytes());
    }

    private byte[] exportBytes() throws Exception {
        return mvc.perform(get("/api/reports/monthly/export?year=2026&month=9"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }
}
