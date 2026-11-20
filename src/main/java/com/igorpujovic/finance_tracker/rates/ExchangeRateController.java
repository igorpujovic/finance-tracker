package com.igorpujovic.finance_tracker.rates;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloading today's rates by hand, for the days the scheduled job did not run or ran
 * before the bank published.
 * <p>
 * It is a warm-up rather than the only way rates reach the database: ADR-0005 has an
 * import fetch whatever date it finds missing, which is what makes a historical statement
 * importable at all. A refresh that cannot reach the bank is the same 503 an import
 * gives, from the same handler.
 */
@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @PostMapping("/refresh")
    public RateRefreshResponse refresh() {
        return service.refreshToday();
    }
}
