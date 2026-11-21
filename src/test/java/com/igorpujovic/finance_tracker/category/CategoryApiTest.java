package com.igorpujovic.finance_tracker.category;

import tools.jackson.databind.ObjectMapper;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import com.igorpujovic.finance_tracker.transaction.TransactionRequest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CategoryApiTest extends PostgresTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CategoryRepository categories;

    /** A category that is not seeded reference data, so no rule and no money point at it. */
    private long createCategory(String name, CategoryType type) throws Exception {
        String created = mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CategoryRequest(name, type))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    private Long idOf(String name) {
        return categories.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seeded category '" + name + "' is missing"))
                .getId();
    }

    @Test
    @DisplayName("the seeded categories are there on a fresh database")
    void seedsReferenceData() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Food')].type").value("EXPENSE"))
                .andExpect(jsonPath("$[?(@.name == 'Salary')].type").value("INCOME"));
    }

    @Test
    @DisplayName("categories come back grouped by type, alphabetical within each")
    void sortsByTypeThenName() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                // Expenses first, then income; alphabetical inside each group. Asserting
                // the boundary is what proves both keys are applied and in which order.
                .andExpect(jsonPath("$[0].name").value("Entertainment"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[4].name").value("Utilities"))
                .andExpect(jsonPath("$[4].type").value("EXPENSE"))
                .andExpect(jsonPath("$[5].name").value("Refunds"))
                .andExpect(jsonPath("$[5].type").value("INCOME"))
                .andExpect(jsonPath("$[6].name").value("Salary"));
    }

    @Test
    @DisplayName("creating a category returns it with an id")
    void createsCategory() throws Exception {
        String body = json.writeValueAsString(new CategoryRequest("Savings", CategoryType.INCOME));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Savings"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("a duplicate name is a conflict, whatever its casing")
    void rejectsDuplicateName() throws Exception {
        String body = json.writeValueAsString(new CategoryRequest("food", CategoryType.EXPENSE));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a blank name is a field error, not a conflict")
    void rejectsBlankName() throws Exception {
        String body = json.writeValueAsString(new CategoryRequest("  ", CategoryType.EXPENSE));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("a category a rule still points at cannot be deleted")
    void refusesToDeleteCategoryUsedByRule() throws Exception {
        mvc.perform(delete("/api/categories/" + idOf("Utilities")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("rule")));
    }

    @Test
    @DisplayName("a category with money filed under it cannot be deleted, with no rule involved")
    void refusesToDeleteCategoryHoldingTransactions() throws Exception {
        long id = createCategory("Hobbies", CategoryType.EXPENSE);

        String transaction = json.writeValueAsString(new TransactionRequest(
                LocalDate.of(2026, 11, 3), "IGRAONICA NOVI SAD",
                new BigDecimal("-1800.00"), "RSD", id));
        mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(transaction))
                .andExpect(status().isCreated());

        // A category this test created, so no seeded rule points at it and the money is
        // the only thing in the way. The reason is asserted and not just the status:
        // both halves of rule 9 answer 409, and a test that read either as the other
        // would stay green while one of them was gone.
        mvc.perform(delete("/api/categories/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("transactions")));
    }

    @Test
    @DisplayName("an unused category is deleted, and deleting it again is a 404")
    void deletesUnusedCategory() throws Exception {
        long id = createCategory("Temporary", CategoryType.EXPENSE);

        mvc.perform(delete("/api/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/" + id)).andExpect(status().isNotFound());
    }
}
