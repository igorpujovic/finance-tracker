package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.support.FakeKurs;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0006: the import is driven through its own endpoint against a real PostgreSQL,
 * because what it has to get right lives in a query and a constraint — the set-based
 * duplicate lookup, the unique index behind it, and the one transaction that writes
 * the rows, the errors and the summary together.
 */
@Transactional
class ImportApiTest extends PostgresTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /**
     * The euro line of {@code sample.csv} is dated 04.11.2026, so from step 4 onwards the
     * file only imports in full against a bank that publishes a euro rate for that day.
     * The rate itself is asserted in {@link ImportRatesTest}; here it only has to exist.
     */
    @BeforeEach
    void publishTheRateTheSampleNeeds() {
        LocalDate november4 = LocalDate.of(2026, 11, 4);
        KURS.publishes(november4, FakeKurs.Rate.of("EUR", november4, "117.2061"));
    }

    private static byte[] fixture(String name) throws Exception {
        try (InputStream in = ImportApiTest.class.getResourceAsStream("/statements/" + name)) {
            assertThat(in).as("the fixture /statements/%s", name).isNotNull();
            return in.readAllBytes();
        }
    }

    private static MockMultipartFile upload(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/csv", content);
    }

    private static MockMultipartFile upload(String name, String content) {
        return upload(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the summary of a successful import */
    private JsonNode importing(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode detailOf(long batchId) throws Exception {
        String body = mvc.perform(get("/api/imports/" + batchId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("the sample statement reports 7 rows: 4 imported, 1 duplicate, 2 rejected")
    void importsTheSampleStatement() throws Exception {
        // The specification's own summary, and the one number in it that moved in step 4:
        // the euro line was the third rejection for as long as nothing filled in history,
        // and is the fourth import now that a missing date is fetched on demand.
        mvc.perform(multipart("/api/imports").file(upload("sample.csv", fixture("sample.csv"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("sample.csv"))
                .andExpect(jsonPath("$.totalRows").value(7))
                .andExpect(jsonPath("$.importedRows").value(4))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(2));
    }

    @Test
    @DisplayName("every source line is accounted for exactly once in the summary")
    void accountsForEverySourceLine() throws Exception {
        JsonNode batch = importing(upload("sample.csv", fixture("sample.csv")));

        assertThat(batch.get("importedRows").asInt()
                + batch.get("duplicateRows").asInt()
                + batch.get("rejectedRows").asInt())
                .isEqualTo(batch.get("totalRows").asInt());
    }

    @Test
    @DisplayName("re-uploading a statement adds nothing and reports every line as a duplicate")
    void reportsASecondUploadAsAllDuplicates() throws Exception {
        JsonNode first = importing(upload("clean.csv", fixture("clean.csv")));
        assertThat(first.get("importedRows").asInt()).isEqualTo(3);

        long before = transactionCount();
        JsonNode second = importing(upload("clean.csv", fixture("clean.csv")));

        assertThat(second.get("totalRows").asInt()).isEqualTo(3);
        assertThat(second.get("duplicateRows").asInt()).isEqualTo(3);
        assertThat(second.get("importedRows").asInt()).isZero();
        assertThat(second.get("rejectedRows").asInt()).isZero();
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("re-uploading the sample adds nothing: what imported is a duplicate, what was rejected is rejected again")
    void reportsASecondSampleUploadConsistently() throws Exception {
        importing(upload("sample.csv", fixture("sample.csv")));
        long before = transactionCount();

        JsonNode second = importing(upload("sample.csv", fixture("sample.csv")));

        assertThat(second.get("totalRows").asInt()).isEqualTo(7);
        assertThat(second.get("importedRows").asInt()).isZero();
        assertThat(second.get("duplicateRows").asInt()).isEqualTo(5);
        assertThat(second.get("rejectedRows").asInt()).isEqualTo(2);
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a line repeated inside one file is a duplicate even on an empty database")
    void catchesADuplicateWithinTheFile() throws Exception {
        JsonNode batch = importing(upload("repeated.csv", """
                date,description,amount,currency
                05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
                05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        assertThat(batch.get("duplicateRows").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a duplicate is counted and skipped, never recorded as a rejected line")
    void doesNotRecordDuplicatesAsErrors() throws Exception {
        JsonNode batch = importing(upload("repeated.csv", """
                date,description,amount,currency
                05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
                05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
                """));

        assertThat(detailOf(batch.get("id").asLong()).get("errors")).isEmpty();
    }

    @Test
    @DisplayName("both copies of a line nothing can convert are rejected, neither is called a duplicate")
    void doesNotCallACopyOfARejectedLineADuplicate() throws Exception {
        // A currency the National Bank does not publish, which from step 4 is the only
        // way a line still has no dinar amount of its own. ADR-0005 reads that as this
        // line's problem rather than the network's, which is what makes the second copy
        // of it a rejection in its own right and not a duplicate of an import that never
        // happened.
        JsonNode batch = importing(upload("repeated-zwl.csv", """
                date,description,amount,currency
                04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,ZWL
                04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,ZWL
                """));

        assertThat(batch.get("importedRows").asInt()).isZero();
        assertThat(batch.get("duplicateRows").asInt())
                .as("a duplicate is a line already imported, and nothing here was")
                .isZero();
        assertThat(batch.get("rejectedRows").asInt()).isEqualTo(2);

        // Every rejected line gets its own error row, or the count on the batch would
        // claim a rejection that nothing in the audit record explains.
        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].reason").value("no exchange rate for ZWL on 2026-11-04"))
                .andExpect(jsonPath("$.errors[1].reason").value("no exchange rate for ZWL on 2026-11-04"));
    }

    @Test
    @DisplayName("a rejected line is numbered by where it really sits in the file, blank lines included")
    void numbersRejectedLinesByTheirPlaceInTheFile() throws Exception {
        JsonNode batch = importing(upload("gappy.csv", """
                date,description,amount,currency
                01.11.2026,PLATA OKTOBAR,95000.00,RSD

                07.11.2026,LIDL 112,abc,RSD
                """));

        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(4))
                .andExpect(jsonPath("$.errors[0].rawLine").value("07.11.2026,LIDL 112,abc,RSD"));
    }

    @Test
    @DisplayName("the detail endpoint lists each rejected line in file order, verbatim, with its reason")
    void reportsRejectedLinesWithReasons() throws Exception {
        JsonNode batch = importing(upload("sample.csv", fixture("sample.csv")));

        // Only the two lines the file itself got wrong are left: the euro line above them
        // is imported from step 4 on, and its row number dropping out of this list is
        // what that looks like from the audit record's side.
        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(7))
                .andExpect(jsonPath("$.errors[0].rawLine").value("31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD"))
                .andExpect(jsonPath("$.errors[1].rowNumber").value(8))
                .andExpect(jsonPath("$.errors[1].rawLine").value("07.11.2026,LIDL 112,abc,RSD"));
    }

    @Test
    @DisplayName("an imported transaction is marked as imported and tied to its batch")
    void writesImportedTransactions() throws Exception {
        long batchId = importing(upload("clean.csv", fixture("clean.csv"))).get("id").asLong();

        mvc.perform(get("/api/transactions").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].source")
                        .value("IMPORT"))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].importBatchId")
                        .value((int) batchId))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].amount")
                        .value(-2450.80))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].amountRsd")
                        .value(-2450.80))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].type")
                        .value("EXPENSE"));

        // Every line of this statement carries a keyword the seeded rules know, so the
        // uncategorized queue — a real state with its own filter — is empty.
        mvc.perform(get("/api/transactions").param("uncategorized", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("the sample statement arrives categorized by the seeded rules, with nothing left over")
    void categorizesWhatItImports() throws Exception {
        importing(upload("sample.csv", fixture("sample.csv")));

        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                // The four lines of the file that survive parsing and conversion. Pinned
                // here so that a row quietly dropped cannot leave the uncategorized count
                // below looking like success.
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.description == 'PLATA OKTOBAR')].categoryName")
                        .value("Salary"))
                .andExpect(jsonPath("$.content[?(@.description == 'MAXI 245 BEOGRAD')].categoryName")
                        .value("Food"))
                .andExpect(jsonPath("$.content[?(@.description == 'EPS SNABDEVANJE')].categoryName")
                        .value("Utilities"))
                // The euro line, which until step 4 was never there to be categorized.
                .andExpect(jsonPath("$.content[?(@.description == 'NETFLIX.COM, AMSTERDAM')].categoryName")
                        .value("Entertainment"));

        mvc.perform(get("/api/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("BUS PLUS is Transport, on a statement whose date the parser accepts")
    void categorizesTransport() throws Exception {
        // The BUS PLUS line of sample.csv is dated 31.11.2026 and is rejected for it,
        // so the keyword needs a line that survives parsing to be matched at all.
        importing(upload("travel.csv", """
                date,description,amount,currency
                30.11.2026,BUS PLUS DOPUNA,-1500.00,RSD
                """));

        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[0].description").value("BUS PLUS DOPUNA"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Transport"));
    }

    @Test
    @DisplayName("a line no keyword matches is imported uncategorized rather than refused")
    void importsWhatNoRuleClaims() throws Exception {
        JsonNode batch = importing(upload("unknown.csv", """
                date,description,amount,currency
                09.11.2026,NEPOZNATI TRGOVAC 77,-800.00,RSD
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        assertThat(batch.get("rejectedRows").asInt()).isZero();

        mvc.perform(get("/api/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("NEPOZNATI TRGOVAC 77"))
                .andExpect(jsonPath("$.content[0].categoryId").doesNotExist());
    }

    @Test
    @DisplayName("a refund is not filed under the expense category its merchant keyword points at")
    void doesNotFileARefundAsAnExpense() throws Exception {
        // ADR-0004 where it actually bites: the same merchant, a positive amount. The
        // seeded POVRACAJ rule is income and outranks nothing it needs to, so the
        // refund lands in Refunds; the MAXI line next to it has no income rule to save
        // it and stays in the queue.
        importing(upload("refunds.csv", """
                date,description,amount,currency
                10.11.2026,EPS SNABDEVANJE POVRACAJ,4870.00,RSD
                11.11.2026,MAXI 245 BEOGRAD POVRAT ROBE,2450.80,RSD
                """));

        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[?(@.description == 'EPS SNABDEVANJE POVRACAJ')].categoryName")
                        .value("Refunds"));

        mvc.perform(get("/api/transactions").param("uncategorized", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("MAXI 245 BEOGRAD POVRAT ROBE"))
                .andExpect(jsonPath("$.content[0].categoryId").doesNotExist());
    }

    @Test
    @DisplayName("the list of past imports carries the summaries but not every rejected line")
    void listsPastImports() throws Exception {
        importing(upload("clean.csv", fixture("clean.csv")));

        mvc.perform(get("/api/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].fileName").value("clean.csv"))
                .andExpect(jsonPath("$.content[0].totalRows").value(3))
                .andExpect(jsonPath("$.content[0].errors").doesNotExist());
    }

    @Test
    @DisplayName("an import batch that does not exist is a 404")
    void refusesUnknownBatch() throws Exception {
        mvc.perform(get("/api/imports/404404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("a file that is not a .csv is refused, and nothing is recorded")
    void refusesNonCsvFile() throws Exception {
        mvc.perform(multipart("/api/imports").file(upload("statement.xlsx", "date,description,amount,currency\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".csv")));

        mvc.perform(get("/api/imports"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a statement with the wrong header is refused before anything is written")
    void refusesWrongHeader() throws Exception {
        mvc.perform(multipart("/api/imports").file(upload("statement.csv", """
                        datum,opis,iznos,valuta
                        01.11.2026,PLATA OKTOBAR,95000.00,RSD
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("date,description,amount,currency")));

        mvc.perform(get("/api/imports"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("an empty upload is refused")
    void refusesEmptyFile() throws Exception {
        mvc.perform(multipart("/api/imports").file(upload("statement.csv", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a statement over five megabytes is refused as a bad request, not accepted and truncated")
    void refusesOversizedFile() throws Exception {
        byte[] oversized = new byte[(int) ImportService.MAX_BYTES + 1];
        Arrays.fill(oversized, (byte) 'x');

        mvc.perform(multipart("/api/imports").file(upload("statement.csv", oversized)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an upload with no file part at all is a bad request")
    void refusesMissingFilePart() throws Exception {
        mvc.perform(multipart("/api/imports"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a statement saved with a byte-order mark still has the header this importer expects")
    void toleratesAByteOrderMark() throws Exception {
        JsonNode batch = importing(upload("clean.csv",
                '﻿' + new String(fixture("clean.csv"), StandardCharsets.UTF_8)));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(3);
    }

    private long transactionCount() throws Exception {
        String body = mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("totalElements").asLong();
    }
}
