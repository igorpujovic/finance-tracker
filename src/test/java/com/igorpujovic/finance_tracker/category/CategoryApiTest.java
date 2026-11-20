package com.igorpujovic.finance_tracker.category;

import tools.jackson.databind.ObjectMapper;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    @DisplayName("an unused category is deleted, and deleting it again is a 404")
    void deletesUnusedCategory() throws Exception {
        String body = json.writeValueAsString(new CategoryRequest("Temporary", CategoryType.EXPENSE));
        String created = mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(delete("/api/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/" + id)).andExpect(status().isNotFound());
    }
}
