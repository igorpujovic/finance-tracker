package com.igorpujovic.finance_tracker.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The report ADR-0002 warns about by name. The largest expenses are the <em>most
 * negative</em> dinar amounts, so this query orders ascending; written the obvious way,
 * with {@code desc}, it returns the smallest expenses and looks plausible enough to
 * survive review.
 */
class TopExpensesTest extends ReportTest {

    /**
     * Twelve expenses of ascending magnitude, so that the default limit of ten bites and
     * the biggest spend is unambiguous; an uncategorized expense large enough to place
     * second, because uncategorized money is spending too; income, which is not an
     * expense whatever its size; and a larger expense one day before the window.
     */
    @BeforeEach
    void aMonthOfSpending() {
        for (int day = 1; day <= 12; day++) {
            save("2026-09-%02d".formatted(day), "RACUN " + day, "-%d00.00".formatted(day), "Food");
        }
        save("2026-09-25", "APOTEKA JANKOVIC", "-1150.00", null);
        save("2026-09-05", "PLATA AVGUST", "120000.00", "Salary");
        save("2026-08-31", "AVGUST ZAKUPNINA", "-99999.00", "Utilities");
    }

    @Test
    @DisplayName("the largest spend comes first, and the list runs ascending from there")
    void theMostNegativeAmountIsFirst() throws Exception {
        JsonNode expenses = topExpenses("limit=5");

        assertThat(expenses.get(0).get("description").asString()).isEqualTo("RACUN 12");
        assertThat(expenses.get(0).get("amountRsd").decimalValue()).isEqualByComparingTo("-1200.00");
        assertThat(amountsOf(expenses)).isSorted();
    }

    @Test
    @DisplayName("uncategorized spending is ranked with everything else")
    void uncategorizedSpendingIsIncluded() throws Exception {
        JsonNode second = topExpenses("limit=5").get(1);

        assertThat(second.get("description").asString()).isEqualTo("APOTEKA JANKOVIC");
        assertThat(second.get("amountRsd").decimalValue()).isEqualByComparingTo("-1150.00");
        assertThat(second.get("categoryId").isNull()).isTrue();
        assertThat(second.get("categoryName").isNull()).isTrue();
    }

    @Test
    @DisplayName("income is never an expense, however large it is")
    void incomeIsExcluded() throws Exception {
        JsonNode expenses = topExpenses("limit=100");

        assertThat(amountsOf(expenses)).allSatisfy(amount -> assertThat(amount).isNegative());
        assertThat(expenses.toString()).doesNotContain("PLATA");
    }

    @Test
    @DisplayName("the limit defaults to ten")
    void tenByDefault() throws Exception {
        assertThat(topExpenses(null)).hasSize(10);
    }

    @Test
    @DisplayName("a limit past a hundred is capped, and the response says what was applied")
    void theLimitIsCappedAtAHundred() throws Exception {
        for (int i = 0; i < 100; i++) {
            save("2026-09-15", "SITNICA " + i, "-1.00", "Food");
        }

        JsonNode report = json.readTree(mvc.perform(
                        get("/api/reports/top-expenses?from=2026-09-01&to=2026-09-30&limit=500"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(report.get("expenses")).hasSize(100);
        assertThat(report.get("limit").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("the window is honoured, so a bigger expense outside it does not win")
    void spendingOutsideTheWindowIsNotRanked() throws Exception {
        assertThat(topExpenses("limit=100").toString()).doesNotContain("AVGUST ZAKUPNINA");
    }

    @Test
    @DisplayName("a limit of zero is a bad request rather than an empty report")
    void aLimitOfZeroIsRefused() throws Exception {
        mvc.perform(get("/api/reports/top-expenses?from=2026-09-01&to=2026-09-30&limit=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a window that ends before it starts is a bad request")
    void aBackwardsWindowIsRefused() throws Exception {
        mvc.perform(get("/api/reports/top-expenses?from=2026-09-30&to=2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode topExpenses(String extraQuery) throws Exception {
        String uri = "/api/reports/top-expenses?from=2026-09-01&to=2026-09-30"
                + (extraQuery == null ? "" : "&" + extraQuery);
        return getJson(uri).get("expenses");
    }

    private static List<BigDecimal> amountsOf(JsonNode expenses) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (JsonNode expense : expenses) {
            amounts.add(expense.get("amountRsd").decimalValue());
        }
        return amounts;
    }
}
