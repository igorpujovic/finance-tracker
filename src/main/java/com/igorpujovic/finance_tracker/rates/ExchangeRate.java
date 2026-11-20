package com.igorpujovic.finance_tracker.rates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The official National Bank of Serbia value of one unit of a currency in dinars on a
 * given date.
 * <p>
 * The date is the one the publication is attributed to, never the one somebody asked
 * about: ADR-0005 stores a rate fetched for a Sunday under the Friday the API says it
 * belongs to, so the table never claims an official rate existed on a Sunday. The
 * weekend-and-holiday rule then needs no calendar — the rate in force is simply the most
 * recent row on or before the date, which is what
 * {@link ExchangeRateRepository#findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc}
 * asks for.
 * <p>
 * There is no row for {@code RSD}. A dinar line is worth its own amount, and a lookup
 * that could answer that question would be a lookup that could get it wrong.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    /** The scale of {@code exchange_rates.rate_to_rsd}, which is {@code numeric(12, 4)}. */
    static final int DECIMALS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "rate_to_rsd", nullable = false, precision = 12, scale = DECIMALS)
    private BigDecimal rateToRsd;

    protected ExchangeRate() {
        // for JPA
    }

    ExchangeRate(PublishedRate published) {
        this.currency = published.currency();
        this.rateDate = published.rateDate();
        this.rateToRsd = published.rateToRsd();
    }

    public Long getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public BigDecimal getRateToRsd() {
        return rateToRsd;
    }
}
