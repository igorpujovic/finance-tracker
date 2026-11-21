package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.support.FakeKurs.Rate;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate pre-pass of ADR-0005 driven through the endpoint it exists for, against a
 * real PostgreSQL and a real HTTP server standing in for Kurs.
 * <p>
 * Everything here is asserted at {@code POST /api/imports} rather than against the rate
 * service, because what the ADR decides is what an upload does: which lines survive a
 * currency the bank does not publish, what is left in the database when the network
 * blinks, and how many times a statement reaches for the network at all.
 * <p>
 * Deliberately <strong>not</strong> {@code @Transactional}, for the reason
 * {@link ImportWriterTest} is not either. Half of what ADR-0005 promises is that a
 * transport failure leaves the database untouched, and inside a test transaction that
 * rolls back regardless, a 503 that wrote nothing is indistinguishable from a 503 that
 * wrote plenty. These tests commit for real and then ask what survived.
 */
class ImportRatesTest extends PostgresTest {

    private static final LocalDate NOVEMBER_4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

    private static final String EURO_LINE = """
            date,description,amount,currency
            04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
            """;

    /** The same euro line dated on a Sunday, which the bank publishes no rate for. */
    private static final String WEEKEND_LINE = """
            date,description,amount,currency
            08.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Nothing here rolls back on its own, so each test clears what it committed. The
     * order is the one the foreign keys allow: errors, then rows, then the batches they
     * point at.
     */
    @AfterEach
    void discardWhateverCommitted() {
        jdbc.update("delete from import_errors");
        jdbc.update("delete from transactions");
        jdbc.update("delete from import_batches");
        jdbc.update("delete from exchange_rates");
    }

    private static MockMultipartFile upload(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode importing(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("a foreign line with no stored rate fetches the date from Kurs and imports at it")
    void convertsAtTheRateItFetches() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        JsonNode batch = importing(upload("netflix.csv", """
                date,description,amount,currency
                04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        assertThat(batch.get("rejectedRows").asInt()).isZero();

        // -9.99 x 117.2061 is -1170.888939, which the dinar amount carries to two
        // decimal places with its sign intact (ADR-0002: a converter that took
        // magnitudes would turn every foreign expense into income).
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].amount").value(-9.99))
                .andExpect(jsonPath("$.content[0].amountRsd").value(-1170.89));

        assertThat(KURS.datesAsked()).containsExactly(NOVEMBER_4);
    }

    @Test
    @DisplayName("a currency the bank does not publish rejects that one line and imports the rest")
    void rejectsOnlyTheLineWhoseCurrencyIsNotPublished() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        JsonNode batch = importing(upload("mixed.csv", """
                date,description,amount,currency
                04.11.2026,PLATA OKTOBAR,95000.00,RSD
                04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
                04.11.2026,MAXI 245 BEOGRAD,-1200.00,ZWL
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(2);
        assertThat(batch.get("rejectedRows").asInt()).isEqualTo(1);

        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(4))
                .andExpect(jsonPath("$.errors[0].reason").value("no exchange rate for ZWL on 2026-11-04"));
    }

    @Test
    @DisplayName("a 503 from the bank aborts the upload and leaves no batch row to explain it")
    void writesNothingAtAllWhenTheBankIsUnavailable() throws Exception {
        KURS.isUnavailableOn(NOVEMBER_4);

        mvc.perform(multipart("/api/imports").file(upload("netflix.csv", """
                        date,description,amount,currency
                        04.11.2026,PLATA OKTOBAR,95000.00,RSD
                        04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
                        """)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value(containsString("Exchange rates are unavailable")));

        // ADR-0005: aborting compensates for nothing, because the pre-pass runs before
        // the transaction opens. The dinar line next to the euro one is not imported
        // either, and there is no half-finished upload in the history to puzzle over.
        mvc.perform(get("/api/imports"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a bank that never answers aborts the upload rather than holding the request open")
    void writesNothingWhenTheBankNeverAnswers() throws Exception {
        KURS.hangsOn(NOVEMBER_4);

        mvc.perform(multipart("/api/imports").file(upload("netflix.csv", EURO_LINE)))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/api/imports")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a body that is not the document the API promised aborts the upload, it does not reject a line")
    void writesNothingWhenTheBankAnswersWithSomethingElse() throws Exception {
        KURS.answersGarbageOn(NOVEMBER_4);

        // The line is perfectly good and its currency is one the bank publishes. Reading
        // this as the line's fault would leave an error row accusing it of being
        // malformed, which is the permanent audit record ADR-0005 refuses to write.
        mvc.perform(multipart("/api/imports").file(upload("netflix.csv", EURO_LINE)))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/api/imports")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a Sunday converts at Friday's rate, and no rate is ever stored under the Sunday")
    void convertsASundayAtTheLastWorkingDaysRate() throws Exception {
        // What the real API does with a date it published nothing for: it answers with
        // the last published rate, saying so in `date_from`.
        KURS.publishes(SUNDAY, Rate.of("EUR", FRIDAY, "117.2061"));

        JsonNode batch = importing(upload("weekend.csv", WEEKEND_LINE));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[0].amountRsd").value(-1170.89));

        assertThat(ratesStoredOn(FRIDAY))
                .as("the date the API attributed the rate to")
                .isEqualTo(1);
        assertThat(ratesStoredOn(SUNDAY))
                .as("the table must never claim an official rate existed on a Sunday")
                .isZero();
    }

    @Test
    @DisplayName("a rate on file from an earlier date is the rate in force, and nothing is downloaded")
    void convertsAtTheLatestStoredRateOnOrBeforeTheDate() throws Exception {
        // Friday's rate is already on file and Saturday's and Sunday's never will be,
        // because the bank does not publish on either. Rule 11 is satisfied here without
        // the network: the most recent rate on or before the Sunday is Friday's, which
        // the pre-pass finds stored and never asks Kurs about. A converter that looked
        // for a rate dated exactly 2026-11-08 would instead download the Sunday — and so
        // would reject this line on the day the bank is unreachable.
        jdbc.update("insert into exchange_rates (currency, rate_date, rate_to_rsd) values (?, ?, ?)",
                "EUR", FRIDAY, new BigDecimal("117.2061"));

        JsonNode batch = importing(upload("weekend.csv", WEEKEND_LINE));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[0].amountRsd").value(-1170.89));

        assertThat(KURS.datesAsked())
                .as("a rate already in force needs no download at all")
                .isEmpty();
        assertThat(ratesStoredOn(SUNDAY))
                .as("and nothing is invented under the Sunday to record what was used")
                .isZero();
    }

    @Test
    @DisplayName("the pre-pass downloads once per missing date, however many lines want it")
    void downloadsOncePerMissingDateRatherThanPerLine() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"),
                Rate.of("USD", NOVEMBER_4, "101.4400"));
        KURS.publishes(FRIDAY, Rate.of("EUR", FRIDAY, "117.3000"));

        // Seven foreign lines over two dates, in two currencies, none of them a
        // duplicate of another. A converter asked line by line would reach for the
        // network seven times.
        JsonNode batch = importing(upload("abroad.csv", """
                date,description,amount,currency
                04.11.2026,NETFLIX ONE,-9.99,EUR
                04.11.2026,NETFLIX TWO,-8.99,EUR
                04.11.2026,NETFLIX THREE,-7.99,EUR
                04.11.2026,AMAZON ONE,-15.00,USD
                06.11.2026,SPOTIFY ONE,-5.99,EUR
                06.11.2026,SPOTIFY TWO,-4.99,EUR
                06.11.2026,PLATA OKTOBAR,95000.00,RSD
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(7);
        assertThat(KURS.datesAsked()).containsExactly(NOVEMBER_4, FRIDAY);
    }

    @Test
    @DisplayName("a date already on file is not downloaded again")
    void readsAStoredRateRatherThanDownloadingItTwice() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        importing(upload("first.csv", EURO_LINE));
        importing(upload("second.csv", """
                date,description,amount,currency
                04.11.2026,SPOTIFY AB,-5.99,EUR
                """));

        assertThat(KURS.datesAsked())
                .as("the second upload needed the same date and found it stored")
                .containsExactly(NOVEMBER_4);
    }

    @Test
    @DisplayName("a currency quoted per hundred units converts at a hundredth of the quote")
    void dividesTheQuoteByItsParity() throws Exception {
        // The issue reads `exchange_middle / parity`, and parity is genuinely not always
        // one: the National Bank quotes the currencies whose unit is worth a fraction of
        // a dinar per hundred of them. Quoted per unit instead, this line would be worth
        // a hundred times too much and look entirely plausible.
        KURS.publishes(NOVEMBER_4, Rate.perHundred("JPY", NOVEMBER_4, "76.1200"));

        JsonNode batch = importing(upload("tokyo.csv", """
                date,description,amount,currency
                04.11.2026,KONBINI TOKYO,-1000.00,JPY
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);

        // 76.12 per hundred yen is 0.7612 per yen, so a thousand yen is 761.20 dinars.
        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[0].amountRsd").value(-761.20));
        assertThat(storedRate("JPY")).isEqualByComparingTo("0.7612");
    }

    @Test
    @DisplayName("a date that fails mid-download leaves none of the dates that succeeded behind")
    void storesNoRatesAtAllWhenOneOfSeveralDatesFails() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));
        KURS.isUnavailableOn(FRIDAY);

        mvc.perform(multipart("/api/imports").file(upload("abroad.csv", """
                        date,description,amount,currency
                        04.11.2026,NETFLIX ONE,-9.99,EUR
                        06.11.2026,SPOTIFY ONE,-5.99,EUR
                        """)))
                .andExpect(status().isServiceUnavailable());

        // ADR-0005 says a transport failure writes nothing, and the rate table is part of
        // the database that sentence is about. Saving each date as it arrived would leave
        // the 4th of November on file after the upload was told it did nothing.
        assertThat(ratesStoredOn(NOVEMBER_4))
                .as("the date that downloaded cleanly before the one that failed")
                .isZero();
        mvc.perform(get("/api/imports")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a line worth less than a hundredth of a dinar is rejected, not rounded to nothing")
    void rejectsALineThatConvertsToNoDinarsAtAll() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("IDR", NOVEMBER_4, "0.0001"));

        JsonNode batch = importing(upload("tiny.csv", """
                date,description,amount,currency
                04.11.2026,PLATA OKTOBAR,95000.00,RSD
                04.11.2026,WARUNG JAKARTA,-0.01,IDR
                """));

        // ADR-0002: zero has no sign and so no nature, and `amount_rsd <> 0` would refuse
        // this row. Caught here it costs its own line; caught at the insert it would roll
        // back the salary line next to it.
        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].reason")
                        .value("-0.01 IDR on 2026-11-04 is worth less than 0.01 RSD"));
    }

    @Test
    @DisplayName("a line worth more dinars than the column holds is rejected, not left to the insert")
    void rejectsALineThatConvertsToMoreDinarsThanCanBeStored() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        JsonNode batch = importing(upload("fortune.csv", """
                date,description,amount,currency
                04.11.2026,PLATA OKTOBAR,95000.00,RSD
                04.11.2026,PRODAJA FIRME,9999999999.99,EUR
                """));

        assertThat(batch.get("importedRows").asInt()).isEqualTo(1);
        mvc.perform(get("/api/imports/" + batch.get("id").asLong()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].reason")
                        .value("9999999999.99 EUR on 2026-11-04 is worth more dinars than can be stored"));
    }

    private BigDecimal storedRate(String currency) {
        return jdbc.queryForObject(
                "select rate_to_rsd from exchange_rates where currency = ?", BigDecimal.class, currency);
    }

    private int ratesStoredOn(LocalDate date) {
        Integer stored = jdbc.queryForObject(
                "select count(*) from exchange_rates where rate_date = ?", Integer.class, date);
        return stored == null ? 0 : stored;
    }
}
