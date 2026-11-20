package com.igorpujovic.finance_tracker.rates;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

/**
 * The Kurs API: the official National Bank of Serbia rates, published without a key.
 * <p>
 * A whole day is downloaded at once rather than one currency at a time, which is what
 * makes the pre-pass of ADR-0005 cost one call per missing date instead of one per line.
 * A statement with euro and dollar spending on the same day asks once, and the twenty-odd
 * other currencies that come back with them warm the table for free.
 * <p>
 * Everything this class decides, it decides from the HTTP outcome, which is the reason
 * ADR-0006 fakes it at the socket rather than behind an interface:
 * <ul>
 *   <li>a day the bank has no rates for is an empty answer, and the lines that wanted one
 *       are rejected individually;
 *   <li>anything else that goes wrong — a refused connection, a timeout, a 5xx, a body
 *       that is not the document this expects — is the network's fault rather than the
 *       statement's, and aborts the upload.
 * </ul>
 */
@Component
class KursClient {

    /** The published path, per {@code https://kurs.resenje.org/doc}. Dates are ISO. */
    private static final String RATES_ON = "/api/v1/rates/{date}";

    private final RestClient http;

    KursClient(RestClient.Builder builder, KursProperties properties) {
        this.http = builder
                .baseUrl(properties.baseUrl())
                // The autoconfigured builder brings this project's Jackson 3 converters;
                // the request factory is replaced so that this client waits for its own
                // configured time rather than however long the default happens to be.
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults()
                                .withTimeouts(properties.connectTimeout(), properties.readTimeout())))
                .build();
    }

    /**
     * @return every currency the bank published for {@code date}, each under the date the
     *         API attributes it to, which for a weekend or a holiday is an earlier day
     * @throws RatesUnavailableException if the bank could not be reached or did not answer
     *                                  with a document this understands
     */
    List<PublishedRate> ratesOn(LocalDate date) {
        try {
            KursRates body = http.get()
                    .uri(RATES_ON, date.toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KursRates.class);
            return body == null ? List.of() : body.published();
        } catch (HttpClientErrorException.NotFound e) {
            // The bank has nothing for that date — a date before its records, or one it
            // has not reached. Not a failure to reach it, so not a reason to abort an
            // upload: every line wanting that date is rejected on its own terms.
            return List.of();
        } catch (RestClientException | HttpMessageConversionException e) {
            throw new RatesUnavailableException(date, e);
        }
    }
}
