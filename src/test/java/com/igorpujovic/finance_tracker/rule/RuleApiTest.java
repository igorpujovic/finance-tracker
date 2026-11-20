package com.igorpujovic.finance_tracker.rule;

import com.igorpujovic.finance_tracker.category.CategoryRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import com.igorpujovic.finance_tracker.transaction.TransactionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rule endpoints against a real PostgreSQL (ADR-0006). What they have to get right
 * that {@link CategorizationTest} cannot see is query-shaped: the rule set is read with
 * its categories in one go, and the seeded rules are the ones a fresh database ships.
 */
@Transactional
class RuleApiTest extends PostgresTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CategoryRepository categories;

    private Long idOf(String name) {
        return categories.findAll().stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seeded category '" + name + "' is missing"))
                .getId();
    }

    private String body(String keyword, Long categoryId, Integer priority) {
        return json.writeValueAsString(new RuleRequest(keyword, categoryId, priority));
    }

    /**
     * A manual transaction, created through its own endpoint rather than through the
     * repository, so that what re-apply is asked to categorize is a transaction the
     * application itself would have stored.
     *
     * @return the id of the created transaction
     */
    private long transaction(String description, String amount, Long categoryId) throws Exception {
        String request = json.writeValueAsString(new TransactionRequest(
                LocalDate.of(2026, 11, 3), description, new BigDecimal(amount), "RSD", categoryId));
        String created = mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    private JsonNode transactionOf(long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/transactions/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode applyRules() throws Exception {
        return json.readTree(mvc.perform(post("/api/rules/apply"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** @return the id of the created rule */
    private long create(String requestBody) throws Exception {
        String created = mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    @Test
    @DisplayName("the seeded rules are there on a fresh database, strongest first")
    void listsSeededRules() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].keyword").value("PLATA"))
                .andExpect(jsonPath("$[0].priority").value(100))
                .andExpect(jsonPath("$[0].categoryName").value("Salary"))
                .andExpect(jsonPath("$[0].categoryType").value("INCOME"))
                .andExpect(jsonPath("$[1].keyword").value("POVRACAJ"))
                .andExpect(jsonPath("$[2].keyword").value("BUS PLUS"))
                .andExpect(jsonPath("$[2].categoryName").value("Transport"));
    }

    @Test
    @DisplayName("a created rule comes back with an id and joins the list in priority order")
    void createsRule() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body("  apoteka  ", idOf("Health"), 70)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/rules/")))
                .andExpect(jsonPath("$.id").isNumber())
                // Stored stripped: a keyword with a stray space would match nothing, and
                // the mistake is invisible in a list of keywords.
                .andExpect(jsonPath("$.keyword").value("apoteka"))
                .andExpect(jsonPath("$.categoryName").value("Health"))
                .andExpect(jsonPath("$.priority").value(70));

        mvc.perform(get("/api/rules"))
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[2].keyword").value("apoteka"));
    }

    @Test
    @DisplayName("a rule with no priority sits at the bottom of the ranking rather than being refused")
    void defaultsPriorityToZero() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"APOTEKA\",\"categoryId\":" + idOf("Health") + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(0));
    }

    @Test
    @DisplayName("a rule's keyword, category and priority can all be changed at once")
    void updatesRule() throws Exception {
        long id = create(body("APOTEKA", idOf("Health"), 70));

        mvc.perform(put("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("APOTEKA BENU", idOf("Entertainment"), 80)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.keyword").value("APOTEKA BENU"))
                .andExpect(jsonPath("$.categoryName").value("Entertainment"))
                .andExpect(jsonPath("$.priority").value(80));
    }

    @Test
    @DisplayName("a deleted rule is gone, and deleting it again is a 404")
    void deletesRule() throws Exception {
        long id = create(body("APOTEKA", idOf("Health"), 70));

        mvc.perform(delete("/api/rules/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(delete("/api/rules/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("a rule pointing at a category that does not exist is a 404")
    void refusesUnknownCategory() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body("APOTEKA", 404404L, 70)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("updating a rule that does not exist is a 404")
    void refusesUnknownRule() throws Exception {
        mvc.perform(put("/api/rules/404404").contentType(MediaType.APPLICATION_JSON)
                        .content(body("APOTEKA", idOf("Health"), 70)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a blank keyword is a field error: it would match every description ever written")
    void refusesBlankKeyword() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", idOf("Health"), 70)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.keyword").exists());
    }

    @Test
    @DisplayName("a rule with no category is a field error rather than a rule that categorizes nothing")
    void refusesMissingCategory() throws Exception {
        mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body("APOTEKA", null, 70)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
    }

    @Test
    @DisplayName("re-apply categorizes an uncategorized transaction a rule matches and reports the count")
    void appliesRulesToUncategorizedTransactions() throws Exception {
        long id = transaction("MAXI 245 BEOGRAD", "-2450.80", null);

        JsonNode applied = applyRules();

        assertThat(applied.get("examined").asInt()).isEqualTo(1);
        assertThat(applied.get("categorized").asInt()).isEqualTo(1);
        assertThat(transactionOf(id).get("categoryName").asText()).isEqualTo("Food");
    }

    @Test
    @DisplayName("re-apply does not touch a transaction that already has a category")
    void leavesCategorizedTransactionsAlone() throws Exception {
        // Health rather than Food: a rule says MAXI is Food, and the point is that the
        // decision already made by hand outranks the rule rather than being overwritten.
        long id = transaction("MAXI 245 BEOGRAD", "-2450.80", idOf("Health"));

        JsonNode applied = applyRules();

        assertThat(applied.get("examined").asInt())
                .as("a categorized transaction is not in the queue re-apply works through")
                .isZero();
        assertThat(applied.get("categorized").asInt()).isZero();
        assertThat(transactionOf(id).get("categoryName").asText()).isEqualTo("Health");
    }

    @Test
    @DisplayName("re-apply counts what it looked at, not only what it categorized")
    void reportsWhatItCouldNotCategorize() throws Exception {
        long matched = transaction("MAXI 245 BEOGRAD", "-2450.80", null);
        long unmatched = transaction("NEPOZNATI TRGOVAC", "-800.00", null);

        JsonNode applied = applyRules();

        assertThat(applied.get("examined").asInt()).isEqualTo(2);
        assertThat(applied.get("categorized").asInt()).isEqualTo(1);
        assertThat(transactionOf(matched).get("categoryName").asText()).isEqualTo("Food");
        assertThat(transactionOf(unmatched).get("categoryId").isNull())
                .as("nothing matched it, so it stays in the uncategorized queue")
                .isTrue();
    }

    @Test
    @DisplayName("re-apply will not put income into an expense category, however well the keyword matches")
    void refusesToCategorizeAcrossTypes() throws Exception {
        // The refund of ADR-0004, seen by re-apply rather than by the rules in isolation:
        // the description is the merchant's, the amount is positive, and the only rule
        // that matches points at an expense category.
        long refund = transaction("MAXI 245 BEOGRAD", "2450.80", null);

        JsonNode applied = applyRules();

        assertThat(applied.get("examined").asInt()).isEqualTo(1);
        assertThat(applied.get("categorized").asInt()).isZero();
        assertThat(transactionOf(refund).get("categoryId").isNull()).isTrue();
    }

    @Test
    @DisplayName("re-apply over an empty queue reports nothing done rather than failing")
    void appliesRulesWithNothingToDo() throws Exception {
        JsonNode applied = applyRules();

        assertThat(applied.get("examined").asInt()).isZero();
        assertThat(applied.get("categorized").asInt()).isZero();
    }
}
