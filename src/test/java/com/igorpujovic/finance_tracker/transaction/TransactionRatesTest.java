package com.igorpujovic.finance_tracker.transaction;

import com.igorpujovic.finance_tracker.support.FakeKurs.Rate;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manual endpoint's side of the rate pre-pass of ADR-0005: one line rather than a
 * statement's worth, through {@code POST} and {@code PUT /api/transactions}, against a
 * real PostgreSQL and a real HTTP server standing in for Kurs.
 * <p>
 * The import path's half of the same decision is {@code ImportRatesTest}, and these
 * assert the rules on this path rather than assuming them from that one: the two now
 * share a converter, and a test that only drove the importer would stay green if the
 * manual door were wired to something else.
 * <p>
 * Deliberately <strong>not</strong> {@code @Transactional}. Half of what ADR-0005
 * promises is that a transport failure leaves the database untouched, and inside a test
 * transaction that rolls back regardless, a 503 that wrote nothing is indistinguishable
 * from a 503 that wrote plenty.
 */
class TransactionRatesTest extends PostgresTest {

    private static final LocalDate NOVEMBER_4 = LocalDate.of(2026, 11, 4);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    /** Nothing here rolls back on its own, so each test clears what it committed. */
    @AfterEach
    void discardWhateverCommitted() {
        jdbc.update("delete from import_errors");
        jdbc.update("delete from transactions");
        jdbc.update("delete from import_batches");
        jdbc.update("delete from exchange_rates");
    }

    private String body(LocalDate date, String description, String amount, String currency)
            throws Exception {
        return json.writeValueAsString(new TransactionRequest(
                date, description, new BigDecimal(amount), currency, null));
    }

    @Test
    @DisplayName("a foreign amount is stored converted at the rate for its own date, and the response carries it")
    void convertsAtTheRateForTheTransactionsOwnDate() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        // -9.99 x 117.2061 is -1170.888939, which the dinar amount carries to two decimal
        // places with its sign intact (ADR-0002).
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "NETFLIX.COM", "-9.99", "EUR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amount").value(-9.99))
                .andExpect(jsonPath("$.amountRsd").value(-1170.89))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        assertThat(KURS.datesAsked()).containsExactly(NOVEMBER_4);
    }

    @Test
    @DisplayName("a Sunday transaction converts at Friday's rate, and no rate is stored under the Sunday")
    void convertsASundayAtTheLastWorkingDaysRate() throws Exception {
        // Rule 11 asserted on this path rather than assumed from the import path: what
        // the real API does with a date it published nothing for is answer with the last
        // published rate, saying so in `date_from`.
        KURS.publishes(SUNDAY, Rate.of("EUR", FRIDAY, "117.2061"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(SUNDAY, "NETFLIX.COM", "-9.99", "EUR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountRsd").value(-1170.89));

        assertThat(ratesStoredOn(FRIDAY))
                .as("the date the API attributed the rate to")
                .isEqualTo(1);
        assertThat(ratesStoredOn(SUNDAY))
                .as("the table must never claim an official rate existed on a Sunday")
                .isZero();
    }

    @Test
    @DisplayName("a date already on file is converted without reaching for the network at all")
    void makesNoHttpCallForADateAlreadyOnFile() throws Exception {
        jdbc.update("insert into exchange_rates (currency, rate_date, rate_to_rsd) values (?, ?, ?)",
                "EUR", NOVEMBER_4, new BigDecimal("117.2061"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "NETFLIX.COM", "-9.99", "EUR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountRsd").value(-1170.89));

        // The whole of ADR-0005 is that the resolution happens outside the transaction
        // the row is written in, and the ordinary case is that it costs nothing: a rate
        // already in force is read from the database and the bank is not asked.
        assertThat(KURS.datesAsked()).isEmpty();
    }

    @Test
    @DisplayName("a dinar amount is stored at its own value without reaching for a rate")
    void needsNoRateForADinarAmount() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "MAXI 245 BEOGRAD", "-2450.80", "RSD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountRsd").value(-2450.80));

        assertThat(KURS.datesAsked()).isEmpty();
    }

    @Test
    @DisplayName("a currency the bank does not publish is refused, naming the currency and the date")
    void refusesACurrencyTheBankDoesNotPublish() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "SOUVENIR HARARE", "-1200.00", "ZWL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("no exchange rate for ZWL on 2026-11-04"));

        mvc.perform(get("/api/transactions")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a bank that cannot be reached is a 503, and the transaction is not written")
    void writesNothingWhenTheBankCannotBeReached() throws Exception {
        KURS.isUnavailableOn(NOVEMBER_4);

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "NETFLIX.COM", "-9.99", "EUR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(containsString("Exchange rates are unavailable")));

        // ADR-0005: the network is not the line's fault, and the refusal costs nothing to
        // compensate for — the resolution ran before the write opened, so there is no
        // half-written row and the same request succeeds once the bank answers.
        mvc.perform(get("/api/transactions")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a conversion too large for the column is refused, naming the amount, currency and date")
    void refusesAConversionTooLargeToStore() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "PRODAJA FIRME", "9999999999.99", "EUR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("9999999999.99 EUR on 2026-11-04 is worth more dinars than can be stored"));

        mvc.perform(get("/api/transactions")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("an amount worth less than a hundredth of a dinar is refused rather than rounded to nothing")
    void refusesAnAmountWorthNoDinarsAtAll() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("IDR", NOVEMBER_4, "0.0001"));

        // ADR-0002: zero has no sign and so no nature, and `amount_rsd <> 0` would refuse
        // the row. Caught here it is a reason; caught at the insert it is a 409 naming a
        // constraint.
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "WARUNG JAKARTA", "-0.01", "IDR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("-0.01 IDR on 2026-11-04 is worth less than 0.01 RSD"));
    }

    @Test
    @DisplayName("an imported foreign row round-trips through PUT with its dinar amount unchanged")
    void amendsAnImportedForeignRowWithoutRestatingIt() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("file", "netflix.csv", "text/csv",
                        """
                        date,description,amount,currency
                        04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
                        """.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());

        String imported = mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.content[0].currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].amountRsd").value(-1170.89))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(imported).get("content").get(0).get("id").asLong();

        // The correction an endpoint limited to dinars could not accept: the row handed
        // back with the currency it came with. The only edit that path allowed was a
        // claim that the row was in dinars, which converts at 1 and would have restated
        // this -9.99 EUR expense as -9.99 RSD — a hundredth of what was spent.
        mvc.perform(put("/api/transactions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "NETFLIX.COM — November", "-9.99", "EUR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("NETFLIX.COM — November"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amountRsd").value(-1170.89))
                .andExpect(jsonPath("$.source").value("IMPORT"));

        // Read back rather than trusted from the response, which is built from the entity
        // the request held: an amendment that was never flushed would answer 200 with
        // every new value in it and leave the row as it was.
        mvc.perform(get("/api/transactions/" + id))
                .andExpect(jsonPath("$.description").value("NETFLIX.COM — November"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amountRsd").value(-1170.89));

        assertThat(KURS.datesAsked())
                .as("the rate the import stored answers the amendment too")
                .containsExactly(NOVEMBER_4);
    }

    @Test
    @DisplayName("a PUT to a transaction that is not there is a 404 and asks the bank nothing")
    void refusesToResolveARateForATransactionThatIsNotThere() throws Exception {
        KURS.publishes(NOVEMBER_4, Rate.of("EUR", NOVEMBER_4, "117.2061"));

        // The conversion happens before the write opens, which is ADR-0005 and is also
        // the ordering that could answer 400 about the currency of a row that does not
        // exist — and reach the National Bank on its behalf. The identifier is checked
        // first for both reasons.
        mvc.perform(put("/api/transactions/999999").contentType(MediaType.APPLICATION_JSON)
                        .content(body(NOVEMBER_4, "NETFLIX.COM", "-9.99", "EUR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(KURS.datesAsked()).isEmpty();
    }

    private int ratesStoredOn(LocalDate date) {
        Integer stored = jdbc.queryForObject(
                "select count(*) from exchange_rates where rate_date = ?", Integer.class, date);
        return stored == null ? 0 : stored;
    }
}
