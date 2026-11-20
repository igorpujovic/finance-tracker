package com.igorpujovic.finance_tracker.transaction;

import tools.jackson.databind.ObjectMapper;
import com.igorpujovic.finance_tracker.category.CategoryRepository;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class TransactionApiTest extends PostgresTest {

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

    private String body(LocalDate date, String description, String amount, String currency, Long categoryId)
            throws Exception {
        return json.writeValueAsString(new TransactionRequest(
                date, description, new BigDecimal(amount), currency, categoryId));
    }

    private long create(String requestBody) throws Exception {
        String created = mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    @Test
    @DisplayName("a negative amount is stored as an expense, signed, with the dinar amount equal to it")
    void createsExpense() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.of(2026, 11, 3), "MAXI 245 BEOGRAD", "-2450.80", "RSD", idOf("Food"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(-2450.80))
                .andExpect(jsonPath("$.amountRsd").value(-2450.80))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.categoryName").value("Food"));
    }

    @Test
    @DisplayName("a zero amount is refused on the manual endpoint, not only on import")
    void rejectsZeroAmount() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.of(2026, 11, 3), "NOTHING", "0.00", "RSD", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountNonZero").exists());
    }

    @Test
    @DisplayName("an expense cannot take an income category")
    void rejectsMismatchedCategory() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.of(2026, 11, 3), "MAXI", "-500.00", "RSD", idOf("Salary"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a foreign currency is refused with a reason until rates arrive in step 4")
    void rejectsForeignCurrencyForNow() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.of(2026, 11, 4), "NETFLIX.COM", "-9.99", "EUR", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RSD")));
    }

    @Test
    @DisplayName("the category can be cleared, returning the transaction to the uncategorized queue")
    void clearsCategory() throws Exception {
        long id = create(body(LocalDate.of(2026, 11, 3), "MAXI", "-500.00", "RSD", idOf("Food")));

        mvc.perform(patch("/api/transactions/" + id + "/category")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist());

        mvc.perform(get("/api/transactions").param("uncategorized", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
    }

    @Test
    @DisplayName("changing the category by hand is refused across types, with the reason")
    void refusesMismatchedCategoryOnPatch() throws Exception {
        long id = create(body(LocalDate.of(2026, 11, 3), "MAXI", "-500.00", "RSD", idOf("Food")));

        // ADR-0004 draws the line in two places. A rule that cannot apply is skipped in
        // silence; a person naming an income category for an expense is told why not,
        // and the category they already had is left alone.
        mvc.perform(patch("/api/transactions/" + id + "/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + idOf("Salary") + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Salary")));

        mvc.perform(get("/api/transactions/" + id))
                .andExpect(jsonPath("$.categoryName").value("Food"));
    }

    @Test
    @DisplayName("listing defaults to twenty per page, newest first")
    void listDefaults() throws Exception {
        create(body(LocalDate.of(2026, 11, 1), "PLATA OKTOBAR", "95000.00", "RSD", idOf("Salary")));
        create(body(LocalDate.of(2026, 11, 9), "LIDL 112", "-1200.00", "RSD", idOf("Food")));

        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-09"));
    }

    @Test
    @DisplayName("filtering by type compares against zero rather than reading a stored column")
    void filtersByType() throws Exception {
        create(body(LocalDate.of(2026, 11, 1), "PLATA OKTOBAR", "95000.00", "RSD", idOf("Salary")));
        create(body(LocalDate.of(2026, 11, 9), "LIDL 112", "-1200.00", "RSD", idOf("Food")));

        mvc.perform(get("/api/transactions").param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("PLATA OKTOBAR"));
    }
}
