package com.igorpujovic.finance_tracker.rates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled half of the rates: on working days, after the bank has published, save
 * the day's rates so that an import of a current statement finds them already there.
 * <p>
 * The cron expression is a property so that a deployment can move it and a test suite can
 * switch it off — a suite is not a working day, and left running it would reach the real
 * National Bank from whatever machine built the project.
 */
@Component
class RateRefreshSchedule {

    private static final Logger log = LoggerFactory.getLogger(RateRefreshSchedule.class);

    private final ExchangeRateService rates;

    RateRefreshSchedule(ExchangeRateService rates) {
        this.rates = rates;
    }

    /**
     * A failure here is logged and dropped rather than rethrown. This job is a warm-up
     * and nothing waits on it: ADR-0005 has the import fetch whatever date it is actually
     * missing, so a morning the bank could not be reached costs the next upload one HTTP
     * call and nobody an explanation.
     */
    @Scheduled(cron = "${app.exchange-rates.refresh-cron}", zone = ExchangeRateService.BELGRADE)
    void saveTodaysRates() {
        try {
            RateRefreshResponse refreshed = rates.refreshToday();
            log.info("Exchange rates for {}: {} published, {} new", refreshed.asOf(),
                    refreshed.published(), refreshed.stored());
        } catch (RatesUnavailableException e) {
            log.warn("Today's exchange rates could not be downloaded; an import that needs them "
                    + "will fetch them itself", e);
        }
    }
}
