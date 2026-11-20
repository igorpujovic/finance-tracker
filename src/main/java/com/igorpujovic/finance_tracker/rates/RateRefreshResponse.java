package com.igorpujovic.finance_tracker.rates;

import java.time.LocalDate;

/**
 * What a refresh of the rates did.
 *
 * @param asOf      the day that was asked about, as Belgrade reckons it
 * @param published how many currencies the National Bank published for it
 * @param stored    how many of those were not already on file. Zero is the ordinary
 *                  answer to a second refresh on one day, and means the table already
 *                  agrees with the bank rather than that anything went wrong.
 */
public record RateRefreshResponse(LocalDate asOf, int published, int stored) {
}
