package com.expenseanalyzer.processing.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the 12-digit UPI reference (RRN) in a transaction description. The bank's row and a UPI app's row for the
 * same payment both carry it, which is how the two are recognised as one payment.
 */
public final class UpiReference {

    private static final Pattern APP = Pattern.compile("UPI Transaction ID: (\\d{12})\\b");
    private static final Pattern STANDALONE = Pattern.compile("(?<!\\d)(\\d{12})(?!\\d)");

    private UpiReference() {
    }

    /** The reference, or null when there is none or more than one candidate (never guessed). */
    public static String find(String description) {
        Matcher app = APP.matcher(description);
        if (app.find()) {
            return app.group(1);
        }
        if (!description.toUpperCase(Locale.ROOT).contains("UPI")) {
            return null;
        }
        Set<String> found = new HashSet<>();
        Matcher m = STANDALONE.matcher(description);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found.size() == 1 ? found.iterator().next() : null;
    }
}
