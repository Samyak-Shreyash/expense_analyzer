package com.expenseanalyzer.merchant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the payee's name out of a UPI person-to-person description. A description is a transfer to a person
 * only when it has a P2A or P2P segment (P2M is a merchant and never matches). The name is the segment right
 * after the 9+ digit UPI reference number, title-cased. Pure; nothing is logged.
 */
public final class PersonNameParser {

    private static final Pattern ANY_SPLIT = Pattern.compile("[/|\\-]");
    private static final Pattern REFERENCE = Pattern.compile("\\d{9,}");
    private static final int MAX_LENGTH = 100;
    private static final Pattern TRANSFER_TOKEN = Pattern.compile("(?<![A-Z0-9])P2[AP](?![A-Z0-9])", Pattern.CASE_INSENSITIVE);

    private PersonNameParser() {
    }

    public static Optional<String> parse(String rawDescription) {
        return segmentAfterReference(rawDescription, PersonNameParser::isTransferToken, true).flatMap(name -> {
            if (name.contains("@") || name.chars().noneMatch(Character::isLetter)) {
                return Optional.empty();
            }
            return Optional.of(titleCase(name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name));
        });
    }

    /** True when the description has a P2A or P2P token anywhere, whether or not a name can be parsed from it. */
    public static boolean isTransfer(String rawDescription) {
        return rawDescription != null && TRANSFER_TOKEN.matcher(rawDescription).find();
    }

    /**
     * The whitespace-collapsed segment right after the 9+ digit reference number (skipping {@code token} segments
     * some banks put there), provided the description has a {@code token} segment and a reference. With
     * {@code dashInName} the delimiter is the one around the reference number, so a dash elsewhere stays in the name
     * (Mary-Anne); otherwise any of / | - splits.
     */
    static Optional<String> segmentAfterReference(String rawDescription, Predicate<String> token, boolean dashInName) {
        if (rawDescription == null) {
            return Optional.empty();
        }
        String trimmed = rawDescription.trim();
        String[] parts = (dashInName ? delimiterAroundReference(trimmed) : ANY_SPLIT).split(trimmed);
        boolean marked = false;
        int reference = -1;
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            marked |= token.test(p);
            if (reference < 0 && REFERENCE.matcher(p).matches()) {
                reference = i;
            }
        }
        int next = reference + 1;
        while (next < parts.length && token.test(parts[next])) {
            next++;
        }
        if (!marked || reference < 0 || next >= parts.length) {
            return Optional.empty();
        }
        String name = parts[next].trim().replaceAll("\\s+", " ");
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    /** The delimiter right before (else right after) the reference number; any of / | - when there is none. */
    private static Pattern delimiterAroundReference(String s) {
        Matcher m = REFERENCE.matcher(s);
        if (m.find()) {
            char before = m.start() > 0 ? s.charAt(m.start() - 1) : ' ';
            char after = m.end() < s.length() ? s.charAt(m.end()) : ' ';
            for (char c : new char[] {before, after}) {
                if (c == '/' || c == '|' || c == '-') {
                    return Pattern.compile(Pattern.quote(String.valueOf(c)));
                }
            }
        }
        return ANY_SPLIT;
    }

    private static boolean isTransferToken(String segment) {
        String s = segment.trim();
        return s.equalsIgnoreCase("P2A") || s.equalsIgnoreCase("P2P");
    }

    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ' || c == '.' || c == '-' || c == '\'';
        }
        return out.toString();
    }
}
