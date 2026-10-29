package com.expenseanalyzer.processing.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Anti-duplication fingerprint (LLD section 10): SHA-256 over user, date, amount in cents, direction,
 * normalised description and occurrence. The occurrence is the index of this row among identical rows
 * in the same statement, so two genuine identical purchases on one day stay distinct while the same
 * row seen again (reprocessing, overlapping statements) collapses. It is not a transaction identity.
 */
public final class TransactionFingerprint {

    private TransactionFingerprint() {
    }

    /** The identity of a row before its occurrence index is applied. */
    public static String baseKey(LocalDate date, BigDecimal cents, String type, String rawDescription) {
        String desc = rawDescription.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return date + "|" + cents.stripTrailingZeros().toPlainString() + "|" + type + "|" + desc;
    }

    public static String of(UUID userId, LocalDate date, BigDecimal cents, String type, String rawDescription,
                            int occurrence) {
        String input = userId + "|" + baseKey(date, cents, type, rawDescription) + "|" + occurrence;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
