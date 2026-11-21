package com.igorpujovic.finance_tracker.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monthly report's one hard promise: its category rows are a complete partition of
 * the month. Nothing is dropped for having no category, nothing is counted twice, and
 * the signed arithmetic of ADR-0002 holds literally rather than after a sign flip.
 */
class MonthlyReportTest extends ReportTest {

    /**
     * The shared September, plus a neighbour one day outside it on each side — which a
     * window written with the wrong comparison would pull in.
     */
    @BeforeEach
    void aSeptemberBetweenTwoNeighbours() {
        saveSeptember();
        save("2026-08-31", "AVGUST ZAKUPNINA", "-50000.00", "Utilities");
        save("2026-10-01", "OKTOBAR ZAKUPNINA", "-70000.00", "Utilities");
    }

    @Test
    @DisplayName("the category totals sum to the balance, with uncategorized money among them")
    void categoryTotalsPartitionTheMonth() throws Exception {
        JsonNode report = getJson("/api/reports/monthly?year=2026&month=9");

        assertThat(report.get("income").decimalValue()).isEqualByComparingTo("120500.00");
        assertThat(report.get("expenses").decimalValue()).isEqualByComparingTo("-9520.79");
        assertThat(report.get("balance").decimalValue()).isEqualByComparingTo("110979.21");

        JsonNode rows = report.get("categories");
        assertThat(rows).hasSize(4);
        assertThat(sumOf(rows, "totalRsd"))
                .isEqualByComparingTo(report.get("balance").decimalValue());

        JsonNode uncategorized = rowFor(rows, null);
        assertThat(uncategorized.get("categoryId").isNull()).isTrue();
        assertThat(uncategorized.get("totalRsd").decimalValue()).isEqualByComparingTo("-499.99");
    }

    @Test
    @DisplayName("income plus expenses equals the balance, with no sign flip in between")
    void incomePlusExpensesIsTheBalance() throws Exception {
        JsonNode report = getJson("/api/reports/monthly?year=2026&month=9");

        BigDecimal income = report.get("income").decimalValue();
        BigDecimal expenses = report.get("expenses").decimalValue();
        assertThat(expenses).isNegative();
        assertThat(income.add(expenses))
                .isEqualByComparingTo(report.get("balance").decimalValue());
    }

    @Test
    @DisplayName("a category's transactions are summed, and its type comes back with it")
    void sumsTheTransactionsOfOneCategory() throws Exception {
        JsonNode food = rowFor(getJson("/api/reports/monthly?year=2026&month=9").get("categories"), "Food");

        assertThat(food.get("totalRsd").decimalValue()).isEqualByComparingTo("-3650.80");
        assertThat(food.get("categoryType").asString()).isEqualTo("EXPENSE");
    }

    /**
     * Rule 13. The euro line is worth 117 times its own number, so the two columns
     * cannot be confused for one another: a report reading {@code amount} would put Food
     * at -3660.79 and the balance 1160.90 dinars out, which is the kind of error that
     * looks like a rounding problem and is not one.
     */
    @Test
    @DisplayName("a foreign transaction is summed at its dinar amount, so currencies add up together")
    void sumsForeignMoneyAtItsDinarAmount() throws Exception {
        saveForeign("2026-09-14", "NETFLIX.COM, AMSTERDAM", "-9.99", "EUR", "-1170.89", "Food");

        JsonNode report = getJson("/api/reports/monthly?year=2026&month=9");

        assertThat(rowFor(report.get("categories"), "Food").get("totalRsd").decimalValue())
                .as("-2450.80 and -1200.00 in dinars, plus -1170.89 the euro line was worth")
                .isEqualByComparingTo("-4821.69");
        assertThat(report.get("expenses").decimalValue()).isEqualByComparingTo("-10691.68");
        assertThat(report.get("balance").decimalValue()).isEqualByComparingTo("109808.32");
        assertThat(sumOf(report.get("categories"), "totalRsd"))
                .as("the partition of rule 14 still holds with a foreign line in the month")
                .isEqualByComparingTo(report.get("balance").decimalValue());
    }

    @Test
    @DisplayName("a month with nothing in it reports zeros rather than nulls")
    void anEmptyMonthIsStillAReport() throws Exception {
        JsonNode report = getJson("/api/reports/monthly?year=2026&month=7");

        assertThat(report.get("categories")).isEmpty();
        assertThat(report.get("income").decimalValue()).isEqualByComparingTo("0");
        assertThat(report.get("expenses").decimalValue()).isEqualByComparingTo("0");
        assertThat(report.get("balance").decimalValue()).isEqualByComparingTo("0");
    }

    /**
     * Money is rounded to two decimals in SQL rather than formatted in Java, so this
     * asserts the wire and not a {@code BigDecimal}: a reader of the JSON gets the same
     * two decimals the CSV export writes.
     */
    @Test
    @DisplayName("money is written with two decimals, whatever the sum came to")
    void writesMoneyWithTwoDecimals() throws Exception {
        String body = mvc.perform(get("/api/reports/monthly?year=2026&month=9"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("-4870.00").contains("120000.00").contains("110979.21");
    }

    @Test
    @DisplayName("a month outside 1 to 12 is a bad request, not a server error")
    void refusesAMonthThatIsNotOne() throws Exception {
        mvc.perform(get("/api/reports/monthly?year=2026&month=13"))
                .andExpect(status().isBadRequest());
    }
}
