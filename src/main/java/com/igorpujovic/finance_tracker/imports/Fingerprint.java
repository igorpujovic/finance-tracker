package com.igorpujovic.finance_tracker.imports;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * What makes a source line recognisable if the same statement is uploaded again.
 * ADR-0003: it identifies the line, not the transaction, so it is computed once from
 * the four fields the file supplied and never recomputed from the values the row
 * later holds.
 * <p>
 * The amount is canonicalised before hashing, so a bank that writes {@code -9.99} one
 * month and {@code -9.990} the next does not produce two fingerprints for one payment.
 * The description is not: case and spacing are part of what the bank wrote, and two
 * descriptions differing in case are not known to describe the same line.
 */
public final class Fingerprint {

    private static final char SEPARATOR = '|';

    private Fingerprint() {
    }

    public static String of(LocalDate date, String description, BigDecimal amount, String currency) {
        String canonical = new StringBuilder()
                .append(date).append(SEPARATOR)
                .append(description).append(SEPARATOR)
                .append(amount.stripTrailingZeros().toPlainString()).append(SEPARATOR)
                .append(currency)
                .toString();
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
