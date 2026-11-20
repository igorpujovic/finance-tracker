package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.imports.ImportBatchRepository;
import com.igorpujovic.finance_tracker.imports.ImportErrorRepository;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reading transactions back once the write has committed and its session is gone.
 * <p>
 * Deliberately <strong>not</strong> {@code @Transactional}, for the same reason
 * {@code ImportWriterTest} is not: a test transaction stays open around the request and
 * keeps a lazily mapped {@code Category} loadable, so it hides exactly the failure this
 * class exists to catch. {@code spring.jpa.open-in-view=false} means the real
 * application has no session left by the time a response is built, and a category that
 * has to be fetched then cannot be.
 * <p>
 * Step 3 is what made this reachable. While every imported transaction arrived
 * uncategorized there was nothing to fetch, and the only categorized rows were the ones
 * a request had just created, whose category was already in memory.
 */
class CategorizedListingTest extends PostgresTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    ImportBatchRepository batches;

    @Autowired
    ImportErrorRepository errors;

    @AfterEach
    void discardWhatCommitted() {
        errors.deleteAll();
        transactions.deleteAll();
        batches.deleteAll();
    }

    @Test
    @DisplayName("a committed imported transaction lists with its category name, not a lazy-loading failure")
    void listsACategoryWrittenByAnEarlierTransaction() throws Exception {
        String statement = """
                date,description,amount,currency
                03.09.2026,MAXI 245 BEOGRAD,-2450.80,RSD
                """;
        String body = mvc.perform(multipart("/api/imports").file(new MockMultipartFile(
                        "file", "committed.csv", "text/csv", statement.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assert json.readTree(body).get("importedRows").asInt() == 1;

        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("MAXI 245 BEOGRAD"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Food"));
    }

    @Test
    @DisplayName("reading one committed transaction by id resolves its category too")
    void readsOneCategoryWrittenByAnEarlierTransaction() throws Exception {
        String statement = """
                date,description,amount,currency
                05.09.2026,EPS SNABDEVANJE,-4870.00,RSD
                """;
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile(
                        "file", "committed.csv", "text/csv", statement.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());

        long id = transactions.findAll().getFirst().getId();

        mvc.perform(get("/api/transactions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Utilities"));
    }
}
