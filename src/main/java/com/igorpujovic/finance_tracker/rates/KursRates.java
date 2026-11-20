package com.igorpujovic.finance_tracker.rates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A day of rates as the Kurs API sends them: an object wrapping a {@code rates} array,
 * each entry carrying the cash and exchange quotes for one currency.
 * <p>
 * Unknown properties are ignored explicitly rather than left to the global Jackson
 * setting, because this is somebody else's document: the published shape has fields this
 * project does not read and may grow more, and none of that is a reason to fail an
 * import. Only the two the specification names are mapped, plus the dates needed to
 * decide which day a rate belongs to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record KursRates(List<Rate> rates) {

    List<PublishedRate> published() {
        return rates == null ? List.of() : rates.stream().map(Rate::published).flatMap(Optional::stream).toList();
    }

    /**
     * @param date     the day that was asked about
     * @param dateFrom the day the publication is attributed to, which on a weekend or a
     *                 holiday is an earlier working day. ADR-0005 stores the rate under
     *                 this one, so the table never claims a rate existed on a Sunday.
     * @param parity   how many units {@code exchangeMiddle} quotes
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rate(
            String code,
            LocalDate date,
            @JsonProperty("date_from") LocalDate dateFrom,
            Integer parity,
            @JsonProperty("exchange_middle") BigDecimal exchangeMiddle) {

        /**
         * An entry missing anything a rate needs is read as a currency this day has no
         * rate for, rather than as the whole download being broken: the lines that wanted
         * it are then rejected one by one, which is what ADR-0005 does with a currency the
         * National Bank does not publish. A body that is not this shape at all is a
         * different matter and never reaches here.
         */
        Optional<PublishedRate> published() {
            LocalDate attributedTo = dateFrom != null ? dateFrom : date;
            if (code == null || attributedTo == null || exchangeMiddle == null
                    || parity == null || parity <= 0) {
                return Optional.empty();
            }
            return Optional.of(PublishedRate.of(
                    code.toUpperCase(Locale.ROOT), attributedTo, exchangeMiddle, parity));
        }
    }
}
