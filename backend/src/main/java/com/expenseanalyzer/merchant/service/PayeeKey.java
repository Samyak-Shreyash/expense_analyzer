package com.expenseanalyzer.merchant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identifies "who was paid" so a user's merchant name for one payment applies to every payment to that payee.
 * A UPI P2M payment is keyed by its payee-name segment (trailing remarks vary per payment); anything else by its
 * normalized description. Pure; nothing is logged.
 */
public final class PayeeKey {

    private static final int MAX_LENGTH = 255;
    private static final Pattern MERCHANT_TOKEN = Pattern.compile("P2M", Pattern.CASE_INSENSITIVE);

    private PayeeKey() {
    }

    /** Upper-cased key, or null when the transaction has neither a P2M payee nor a normalized description. */
    public static String of(String rawDescription, String normalizedDescription) {
        return ofP2m(rawDescription).orElseGet(() -> clean(normalizedDescription == null ? "" : normalizedDescription));
    }

    /** The key when it comes from a UPI P2M payee segment (a merchant); empty for any other description. */
    public static Optional<String> ofP2m(String rawDescription) {
        return PersonNameParser.segmentAfterReference(rawDescription, s -> MERCHANT_TOKEN.matcher(s.trim()).matches(), false)
                .filter(s -> s.chars().anyMatch(Character::isLetter))
                .map(PayeeKey::clean);
    }

    private static String clean(String s) {
        String key = s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return key.isEmpty() ? null : key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH) : key;
    }
}
