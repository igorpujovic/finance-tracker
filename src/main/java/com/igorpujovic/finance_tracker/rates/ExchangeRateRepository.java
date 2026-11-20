package com.igorpujovic.finance_tracker.rates;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * The rate in force on a date: the most recent one published on or before it. This
     * one query is the entire weekend-and-holiday rule of ADR-0005 — Friday's rate simply
     * is the most recent one on or before Sunday — and there is no calendar anywhere in
     * this project because of it. {@code idx_exchange_rates_lookup} is the index it rides.
     */
    Optional<ExchangeRate> findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
            String currency, LocalDate date);

    /**
     * Which currencies are already on file for a date, asked once per downloaded date
     * rather than once per currency in the response.
     */
    @Query("select r.currency from ExchangeRate r where r.rateDate = :date")
    Set<String> findCurrenciesByRateDate(@Param("date") LocalDate date);
}
