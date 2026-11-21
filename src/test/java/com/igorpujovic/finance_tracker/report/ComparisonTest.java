package com.igorpujovic.finance_tracker.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The comparison covers every category active in <em>either</em> month, which is the
 * whole reason it is a full outer join: a category that stopped is as visible as one
 * that started, and neither side can be the one that decides which rows exist.
 * <p>
 * Percent is {@code change / ABS(previous)}, so a positive percent always means the
 * position improved — spending less on Food reads the same as earning more (ADR-0002).
 */
class ComparisonTest extends ReportTest {

    /**
     * August against September, with one category in each of the five states a full
     * outer join has to handle: in both months, only in the previous one as an expense,
     * only in the previous one as income, only in the current one, and uncategorized on
     * both sides — the state a join on equality silently splits in two, because
     * {@code null = null} is not true.
     */
    @BeforeEach
    void anAugustAndASeptember() {
        save("2026-08-04", "MAXI 245 BEOGRAD", "-23450.00", "Food");
        save("2026-09-03", "MAXI 245 BEOGRAD", "-2450.80", "Food");

        save("2026-08-09", "BUS PLUS DOPUNA", "-5000.00", "Transport");

        save("2026-08-05", "PLATA JUL", "120000.00", "Salary");

        save("2026-09-18", "NETFLIX.COM", "-1490.00", "Entertainment");

        save("2026-08-21", "APOTEKA JANKOVIC", "-300.00", null);
        save("2026-09-20", "APOTEKA JANKOVIC", "-700.00", null);
    }

    @Test
    @DisplayName("income last month and none this month is -100%")
    void anIncomeCategoryThatStoppedIsMinusOneHundredPercent() throws Exception {
        JsonNode salary = rowFor(comparisonOfSeptember(), "Salary");

        assertThat(salary.get("previous").decimalValue()).isEqualByComparingTo("120000.00");
        assertThat(salary.get("current").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(salary.get("change").decimalValue()).isEqualByComparingTo("-120000.00");
        assertThat(salary.get("percent").decimalValue()).isEqualByComparingTo("-100.00");
    }

    /**
     * The same arithmetic on an expense category, where it comes out the other way
     * round: spending stopped, so the change is positive and the percent is +100%.
     * ADR-0002 is what makes that the right answer rather than a sign bug — a positive
     * change means the position improved, and no longer spending on buses is an
     * improvement of exactly the amount that was being spent.
     */
    @Test
    @DisplayName("spending last month and none this month is +100%, because the position improved")
    void anExpenseCategoryThatStoppedIsPlusOneHundredPercent() throws Exception {
        JsonNode transport = rowFor(comparisonOfSeptember(), "Transport");

        assertThat(transport.get("previous").decimalValue()).isEqualByComparingTo("-5000.00");
        assertThat(transport.get("current").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(transport.get("change").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(transport.get("percent").decimalValue()).isEqualByComparingTo("100.00");
    }

    /** ADR-0002's worked example, to the digit: Food from -23450.00 to -2450.80 is +89.55%. */
    @Test
    @DisplayName("spending less on Food is a positive change and a positive percent")
    void spendingLessIsAnImprovement() throws Exception {
        JsonNode food = rowFor(comparisonOfSeptember(), "Food");

        assertThat(food.get("change").decimalValue()).isEqualByComparingTo("20999.20");
        assertThat(food.get("percent").decimalValue()).isEqualByComparingTo("89.55");
    }

    @Test
    @DisplayName("a category that started this month has an empty percent, not a division by zero")
    void aCategoryThatStartedHasNoPercent() throws Exception {
        JsonNode entertainment = rowFor(comparisonOfSeptember(), "Entertainment");

        assertThat(entertainment.get("previous").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(entertainment.get("current").decimalValue()).isEqualByComparingTo("-1490.00");
        assertThat(entertainment.get("change").decimalValue()).isEqualByComparingTo("-1490.00");
        assertThat(entertainment.get("percent").isNull()).isTrue();
    }

    /**
     * The uncategorized bucket joins to itself on a null key. Written as
     * {@code prev.category_id = cur.category_id} it does not, and the month reports its
     * uncategorized money twice — once with a zero current and once with a zero
     * previous — which still sums correctly and so passes every other assertion here.
     */
    @Test
    @DisplayName("uncategorized money is one row across both months, not two half-rows")
    void uncategorizedJoinsToItself() throws Exception {
        JsonNode rows = comparisonOfSeptember();

        assertThat(rows).hasSize(5);
        JsonNode uncategorized = rowFor(rows, null);
        assertThat(uncategorized.get("previous").decimalValue()).isEqualByComparingTo("-300.00");
        assertThat(uncategorized.get("current").decimalValue()).isEqualByComparingTo("-700.00");
        assertThat(uncategorized.get("change").decimalValue()).isEqualByComparingTo("-400.00");
        assertThat(uncategorized.get("percent").decimalValue()).isEqualByComparingTo("-133.33");
    }

    @Test
    @DisplayName("January compares against the previous December")
    void januaryLooksBackAcrossTheYear() throws Exception {
        save("2026-12-20", "EPS SNABDEVANJE", "-8000.00", "Utilities");
        save("2027-01-10", "EPS SNABDEVANJE", "-2000.00", "Utilities");

        JsonNode report = getJson("/api/reports/comparison?year=2027&month=1");

        assertThat(report.get("previousYear").asInt()).isEqualTo(2026);
        assertThat(report.get("previousMonth").asInt()).isEqualTo(12);

        JsonNode utilities = rowFor(report.get("categories"), "Utilities");
        assertThat(utilities.get("previous").decimalValue()).isEqualByComparingTo("-8000.00");
        assertThat(utilities.get("current").decimalValue()).isEqualByComparingTo("-2000.00");
        assertThat(utilities.get("change").decimalValue()).isEqualByComparingTo("6000.00");
    }

    private JsonNode comparisonOfSeptember() throws Exception {
        return getJson("/api/reports/comparison?year=2026&month=9").get("categories");
    }
}
