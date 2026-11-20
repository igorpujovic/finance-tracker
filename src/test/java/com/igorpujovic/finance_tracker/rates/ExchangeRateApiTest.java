package com.igorpujovic.finance_tracker.rates;

import com.igorpujovic.finance_tracker.support.FakeKurs.Rate;
import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The warm-up half of the rates: the manual refresh endpoint and the job that runs it on
 * working days. Neither is how a historical statement gets its rates — ADR-0005 has the
 * import fetch whatever date it finds missing — so what these have to get right is
 * narrower: store under the attributed date, do not write a rate twice, and fail
 * harmlessly.
 * <p>
 * Not {@code @Transactional}, so that "a second refresh stores nothing new" is a statement
 * about rows that committed rather than about rows awaiting a rollback.
 */
class ExchangeRateApiTest extends PostgresTest {

    /** What the endpoint means by today: the day the National Bank is having. */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Belgrade"));

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RateRefreshSchedule schedule;

    @AfterEach
    void discardWhateverCommitted() {
        jdbc.update("delete from exchange_rates");
    }

    private void refresh(int published, int stored) throws Exception {
        mvc.perform(post("/api/exchange-rates/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(TODAY.toString()))
                .andExpect(jsonPath("$.published").value(published))
                .andExpect(jsonPath("$.stored").value(stored));
    }

    private int ratesStoredOn(LocalDate date) {
        Integer stored = jdbc.queryForObject(
                "select count(*) from exchange_rates where rate_date = ?", Integer.class, date);
        return stored == null ? 0 : stored;
    }

    @Test
    @DisplayName("a refresh downloads today's rates and reports what it kept")
    void storesTodaysRates() throws Exception {
        KURS.publishes(TODAY, Rate.of("EUR", TODAY, "117.6412"), Rate.of("USD", TODAY, "101.4400"));

        refresh(2, 2);

        assertThat(ratesStoredOn(TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("a second refresh on the same day keeps nothing new and refuses nothing")
    void keepsNothingTwice() throws Exception {
        KURS.publishes(TODAY, Rate.of("EUR", TODAY, "117.6412"), Rate.of("USD", TODAY, "101.4400"));

        refresh(2, 2);
        // The unique index on (currency, rate_date) would refuse a second write, so a
        // rate already on file has to be left alone rather than saved again. Nobody
        // refreshing twice should see a 409.
        refresh(2, 0);

        assertThat(ratesStoredOn(TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("a rate is stored under the date the bank attributes it to, not the day it was asked for")
    void storesUnderTheAttributedDate() throws Exception {
        LocalDate attributedTo = TODAY.minusDays(2);
        KURS.publishes(TODAY, Rate.of("EUR", attributedTo, "117.6412"));

        refresh(1, 1);

        assertThat(ratesStoredOn(attributedTo)).isEqualTo(1);
        assertThat(ratesStoredOn(TODAY))
                .as("the table must not claim the bank published a rate today when it did not")
                .isZero();
    }

    @Test
    @DisplayName("a refresh that cannot reach the bank is a 503 that stores nothing")
    void reportsAnUnreachableBank() throws Exception {
        KURS.isUnavailableOn(TODAY);

        mvc.perform(post("/api/exchange-rates/refresh"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        assertThat(ratesStoredOn(TODAY)).isZero();
    }

    @Test
    @DisplayName("the scheduled job swallows an unreachable bank rather than dying on it")
    void survivesAnUnreachableBankOnItsSchedule() {
        KURS.isUnavailableOn(TODAY);

        // The job is a warm-up and nothing waits on it. Letting the exception out would
        // put a stack trace in the log every working morning the network hiccuped, for a
        // condition the next upload fixes by itself.
        assertThatCode(schedule::saveTodaysRates).doesNotThrowAnyException();

        assertThat(ratesStoredOn(TODAY)).isZero();
    }

    @Test
    @DisplayName("the scheduled job saves the day's rates when it can reach the bank")
    void savesTheDaysRatesOnItsSchedule() {
        KURS.publishes(TODAY, Rate.of("EUR", TODAY, "117.6412"));

        schedule.saveTodaysRates();

        assertThat(ratesStoredOn(TODAY)).isEqualTo(1);
    }
}
