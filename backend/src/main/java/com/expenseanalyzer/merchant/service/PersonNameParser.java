package com.expenseanalyzer.merchant.service;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pulls the payee's name out of a UPI person-to-person description. A description is a transfer to a person
 * only when it has a P2A or P2P segment (P2M is a merchant and never matches). The name is the segment right
 * after the 9+ digit UPI reference number, title-cased. Pure; nothing is logged.
 */
public final class PersonNameParser {

    private static final Pattern SEGMENT_SPLIT = Pattern.compile("[/|\\-]");
    private static final Pattern REFERENCE = Pattern.compile("\\d{9,}");
    private static final int MAX_LENGTH = 100;

    private PersonNameParser() {
    }

    public static Optional<String> parse(String rawDescription) {
        return segmentAfterReference(rawDescription, PersonNameParser::isTransferToken).flatMap(name -> {
            if (name.contains("@") || name.chars().noneMatch(Character::isLetter)) {
                return Optional.empty();
            }
            return Optional.of(titleCase(name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name));
        });
    }

    /**
     * The whitespace-collapsed segment right after the 9+ digit reference number (skipping {@code token} segments
     * some banks put there), provided the description has a {@code token} segment and a reference.
     */
    static Optional<String> segmentAfterReference(String rawDescription, Predicate<String> token) {
        if (rawDescription == null) {
            return Optional.empty();
        }
        String[] parts = SEGMENT_SPLIT.split(rawDescription.trim());
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

    private static boolean isTransferToken(String segment) {
        String s = segment.trim();
        return s.equalsIgnoreCase("P2A") || s.equalsIgnoreCase("P2P");
    }

    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ' || c == '.';
        }
        return out.toString();
    }
}
