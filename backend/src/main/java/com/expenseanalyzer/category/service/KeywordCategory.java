package com.expenseanalyzer.category.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Category names suggested by whole-word keywords in a raw description, for rows with no merchant and
 * for new merchants that have no hint. */
public final class KeywordCategory {

    // order matters: the first keyword found wins
    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("EMI", CategoryService.LOAN_EMI);
        KEYWORDS.put("RENT", "Rent");
        for (String k : new String[] {"INSURANCE", "PREMIUM", "POLICYBAZAAR", "LIC"}) {
            KEYWORDS.put(k, "Insurance");
        }
        for (String k : new String[] {"PHARMACY", "HOSPITAL", "CLINIC", "APOLLO", "MEDPLUS", "PHARMEASY"}) {
            KEYWORDS.put(k, "Health");
        }
        for (String k : new String[] {"CAFE", "COFFEE", "TEA", "CHAI", "RESTAURANT", "DHABA", "CANTEEN", "MESS",
                "TIFFIN", "BAKERY", "BAKERS", "SWEETS", "BIRYANI", "PIZZA", "BURGER", "JUICE", "KITCHEN", "EATERY",
                "FOOD", "FOODS"}) {
            KEYWORDS.put(k, "Food & Dining");
        }
    }

    private KeywordCategory() {
    }

    public static Optional<String> of(String rawDescription) {
        if (rawDescription == null) {
            return Optional.empty();
        }
        Set<String> tokens = new HashSet<>(Arrays.asList(rawDescription.toUpperCase(Locale.ROOT).split("[^A-Z]+")));
        return KEYWORDS.entrySet().stream().filter(e -> tokens.contains(e.getKey())).map(Map.Entry::getValue).findFirst();
    }
}
