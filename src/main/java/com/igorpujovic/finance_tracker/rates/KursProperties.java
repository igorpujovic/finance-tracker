package com.igorpujovic.finance_tracker.rates;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the Kurs API is and how long this project is willing to wait for it.
 * <p>
 * The base URL is configurable because of ADR-0006: the tests bind a real HTTP server to
 * it rather than mocking a Java interface, since the distinction the import rests on is a
 * distinction between HTTP outcomes.
 *
 * @param readTimeout a rate is resolved while somebody waits for an upload to be
 *                    accepted, so the client gives up rather than holding the request
 *                    open. Giving up is a 503 that writes nothing, which costs the person
 *                    one re-upload and ADR-0005 prefers to a half-explained import.
 */
@ConfigurationProperties("app.kurs")
record KursProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
