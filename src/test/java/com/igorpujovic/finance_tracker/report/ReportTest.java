package com.igorpujovic.finance_tracker.report;

import com.igorpujovic.finance_tracker.category.Category;
import com.igorpujovic.finance_tracker.category.CategoryRepository;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import com.igorpujovic.finance_tracker.transaction.Transaction;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What every report test needs: a month's worth of committed transactions and a way to
 * read a report back over HTTP.
 * <p>
 * Deliberately not {@code @Transactional}. A report is an aggregation query over
 * committed rows, and a test transaction would hold uncommitted writes that the native
 * queries of {@link ReportRepository} may or may not see — a question about Hibernate's
 * flush rather than about the reports. Rows are committed by {@link #save} and removed
 * again afterwards, exactly as the application would leave them.
 */
abstract class ReportTest extends PostgresTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    CategoryRepository categories;

    @AfterEach
    void discardWhatCommitted() {
        transactions.deleteAll();
    }

    /**
     * One dinar transaction. The sign of {@code amountRsd} is the whole of its
     * income-or-expense nature (ADR-0002), and a null category name leaves it
     * uncategorized — a state reports have to account for rather than skip.
     * <p>
     * Written through the repository rather than {@code POST /api/transactions} because
     * the write path is step 1's subject and has its own tests; what a report needs is
     * rows, including uncategorized ones that no single request creates.
     */
    void save(String date, String description, String amountRsd, String categoryName) {
        BigDecimal amount = new BigDecimal(amountRsd);
        transactions.save(Transaction.manual(
                LocalDate.parse(date), description, amount, "RSD", amount, category(categoryName)));
    }

    /**
     * One foreign transaction: what the bank charged, in the currency it charged it in,
     * and separately what that was worth in dinars on the day (GLOSSARY: dinar amount).
     * The two differ here on purpose, and by a wide margin — a report that summed the
     * wrong column would be right about every dinar row and silently wrong about this
     * one, which is the shape of mistake rule 13 exists to catch.
     */
    void saveForeign(String date, String description, String amount, String currency,
                     String amountRsd, String categoryName) {
        transactions.save(Transaction.manual(LocalDate.parse(date), description,
                new BigDecimal(amount), currency, new BigDecimal(amountRsd), category(categoryName)));
    }

    /**
     * One September, with money in every shape a report has to account for: income, two
     * transactions in one expense category, an uncategorized expense and an uncategorized
     * refund. Income is 120500.00, expenses are -9520.79, and the balance is 110979.21.
     * <p>
     * Shared by the monthly report and its export, because the point of the export test
     * is that two renderings of <em>the same month</em> agree.
     */
    void saveSeptember() {
        save("2026-09-05", "PLATA AVGUST", "120000.00", "Salary");
        save("2026-09-03", "MAXI 245 BEOGRAD", "-2450.80", "Food");
        save("2026-09-12", "LIDL NOVI BEOGRAD", "-1200.00", "Food");
        save("2026-09-07", "EPS SNABDEVANJE", "-4870.00", "Utilities");
        save("2026-09-20", "APOTEKA JANKOVIC", "-999.99", null);
        save("2026-09-22", "POVRACAJ NEPOZNATO", "500.00", null);
    }

    private Category category(String name) {
        if (name == null) {
            return null;
        }
        return categories.findAll().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No seeded category named " + name));
    }

    /** Sums one decimal field across a JSON array, so an invariant can be asserted over it. */
    static BigDecimal sumOf(JsonNode rows, String field) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode row : rows) {
            total = total.add(row.get(field).decimalValue());
        }
        return total;
    }

    /** The row for a category by name, or the uncategorized row when the name is null. */
    static JsonNode rowFor(JsonNode rows, String categoryName) {
        for (JsonNode row : rows) {
            JsonNode name = row.get("categoryName");
            if (categoryName == null ? name.isNull() : categoryName.equals(name.asString())) {
                return row;
            }
        }
        throw new AssertionError("No row for category " + categoryName + " in " + rows);
    }

    JsonNode getJson(String uri) throws Exception {
        return json.readTree(mvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
