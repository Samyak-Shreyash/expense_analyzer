package com.expenseanalyzer.transaction.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Recognises the account holder's own name in a bank description, where it is often truncated or run into the next
 * word ("SAMYAKSH HDFCB ANK"). Nothing here logs or stores the name.
 */
public final class OwnName {

    private static final int MIN_LETTERS = 6;

    private OwnName() {
    }

    /**
     * True when some word of {@code text} starts a run of letters that matches the start of the full name for at least
     * max(6, first name length + 2) letters, or for the whole name. Names under 6 letters never match.
     */
    public static boolean mentions(String fullName, String text) {
        if (fullName == null || fullName.isBlank() || text == null) {
            return false;
        }
        List<String> name = tokens(fullName);
        String full = String.join("", name);
        if (full.length() < MIN_LETTERS) {
            return false;
        }
        int needed = Math.max(MIN_LETTERS, name.get(0).length() + 2);
        List<String> words = tokens(text);
        for (int i = 0; i < words.size(); i++) {
            String rest = String.join("", words.subList(i, words.size()));
            int common = commonPrefix(rest, full);
            if (common >= needed || common == full.length()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokens(String s) {
        return Arrays.stream(s.toUpperCase(Locale.ROOT).split("[^\\p{L}]+")).filter(t -> !t.isEmpty()).toList();
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
