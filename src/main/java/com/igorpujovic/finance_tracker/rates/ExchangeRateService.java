package com.igorpujovic.finance_tracker.rates;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The rate pre-pass of ADR-0005: the distinct currency-and-date pairs a statement's valid
 * lines need, resolved from the stored rates where possible and from the Kurs API where
 * there is a genuine gap, before any import transaction opens.
 * <p>
 * This class carries no {@code @Transactional} anywhere on that path, and that is the
 * whole point of it. A lazy converter asked inside the transaction would open one and then
 * make up to twenty-two sequential HTTP calls while holding it — a month of card spending
 * abroad is about that many working days — with a connection-pool thread and a PostgreSQL
 * transaction both waiting on a third party. Resolving first reduces that to the number of
 * genuinely missing dates, usually zero, and leaves all of it outside.
 */
@Service
public class ExchangeRateService {

    /**
     * The National Bank publishes on Belgrade time, so which day "today" is, is a fact
     * about the bank rather than about the machine running this. A constant and not a
     * property for the same reason — a deployment elsewhere does not change when the
     * National Bank of Serbia publishes.
     */
    static final String BELGRADE = "Europe/Belgrade";

    private final ExchangeRateRepository stored;
    private final KursClient kurs;

    public ExchangeRateService(ExchangeRateRepository stored, KursClient kurs) {
        this.stored = stored;
        this.kurs = kurs;
    }

    /**
     * @param needed every pair the lines could use, duplicates and dinar lines included —
     *               sifting them is this method's job, not the caller's
     * @throws RatesUnavailableException if a gap had to be downloaded and could not be
     */
    public ResolvedRates resolve(Collection<CurrencyOnDate> needed) {
        Set<CurrencyOnDate> wanted = needed.stream()
                .filter(Predicate.not(CurrencyOnDate::isDinar))
                .collect(Collectors.toSet());

        Map<CurrencyOnDate, BigDecimal> found = rateInForceFor(wanted);
        Set<CurrencyOnDate> gaps = wanted.stream()
                .filter(Predicate.not(found::containsKey))
                .collect(Collectors.toSet());

        if (!gaps.isEmpty()) {
            // One download per missing date, not one per missing pair and certainly not
            // one per line: a day's download carries every currency the bank published
            // that day, so a second currency on a date already fetched costs nothing.
            //
            // Every date is downloaded before any of it is saved, because ADR-0005 says a
            // transport failure writes nothing. Saving each date as it arrived would leave
            // a statement whose fourth date timed out with the first three days of rates
            // committed and a 503 in its hand — a database that was touched by an upload
            // the caller was told did nothing. The cost is that the retry downloads all
            // four again, which is the cheaper half of the trade.
            saveNew(gaps.stream()
                    .map(CurrencyOnDate::date)
                    .distinct()
                    .sorted()
                    .flatMap(date -> kurs.ratesOn(date).stream())
                    .toList());
            found.putAll(rateInForceFor(gaps));
        }
        return new ResolvedRates(found);
    }

    /**
     * Today's rates, downloaded and kept. Used by the scheduled job and by the manual
     * endpoint, which differ only in what triggers them.
     *
     * @throws RatesUnavailableException if the bank could not be reached
     */
    public RateRefreshResponse refreshToday() {
        LocalDate today = LocalDate.now(ZoneId.of(BELGRADE));
        List<PublishedRate> published = kurs.ratesOn(today);
        return new RateRefreshResponse(today, published.size(), saveNew(published));
    }

    private Map<CurrencyOnDate, BigDecimal> rateInForceFor(Set<CurrencyOnDate> wanted) {
        Map<CurrencyOnDate, BigDecimal> found = new HashMap<>();
        for (CurrencyOnDate one : wanted) {
            stored.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(one.currency(), one.date())
                    .ifPresent(rate -> found.put(one, rate.getRateToRsd()));
        }
        return found;
    }

    /**
     * Saves what was downloaded, under the dates the API attributed the rates to rather
     * than the dates that were asked for. One day's download really does scatter: asking
     * for a Friday in November returns that Friday's euro rate alongside a Bulgarian lev
     * rate attributed to the previous December, because the bank has not changed it since.
     * <p>
     * A rate already on file is left alone rather than written again. A download is
     * triggered by one currency having no rate and brings back every other currency with
     * it, some of which may already be stored under that very date — which the unique
     * index on {@code (currency, rate_date)} would otherwise refuse. The transaction this
     * opens is the repository's own, after every HTTP call has already returned.
     *
     * @return how many of these rates were not already on file
     */
    private int saveNew(List<PublishedRate> published) {
        int saved = 0;
        for (Map.Entry<LocalDate, List<PublishedRate>> day
                : published.stream().collect(Collectors.groupingBy(PublishedRate::rateDate)).entrySet()) {
            Set<String> alreadyOnFile = stored.findCurrenciesByRateDate(day.getKey());
            List<ExchangeRate> toSave = day.getValue().stream()
                    .filter(rate -> !alreadyOnFile.contains(rate.currency()))
                    .map(ExchangeRate::new)
                    .toList();
            stored.saveAll(toSave);
            saved += toSave.size();
        }
        return saved;
    }
}
