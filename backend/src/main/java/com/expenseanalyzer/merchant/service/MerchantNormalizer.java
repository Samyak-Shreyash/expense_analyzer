package com.expenseanalyzer.merchant.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic merchant normalisation (LLD section 11): upper-case, strip bank/UPI noise, then match
 * whole-token aliases. No match means no merchant; nothing is inferred from free text.
 * Changing the rules changes analytics, so bump {@link #RULES_VERSION} and extend the tests.
 */
@Component
public class MerchantNormalizer {

    public static final int RULES_VERSION = 1;
    public static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.95");

    private static final Pattern UPI_HANDLE = Pattern.compile("[A-Z0-9._-]+@[A-Z]+");
    private static final Pattern NON_WORD = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern IFSC = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");
    private static final Pattern REFERENCE = Pattern.compile("(?=.*\\d)[A-Z0-9]{6,}|\\d+");

    /** Payment rails, bank shorthand and UPI app handles: they say how money moved, not to whom. */
    private static final Set<String> NOISE = Set.of(
            "UPI", "POS", "NEFT", "IMPS", "RTGS", "ACH", "ECS", "NACH", "D", "CR", "DR", "REF", "TXN",
            "INB", "MB", "IB", "VPS", "PAYTM", "OKAXIS", "OKHDFCBANK", "OKICICI", "OKSBI", "YBL", "IBL", "AXL");

    private record Rule(List<String> tokens, String canonicalName, String categoryHint) {
        Rule(String alias, String canonicalName, String categoryHint) {
            this(Arrays.asList(alias.split(" ")), canonicalName, categoryHint);
        }
    }

    /** Checked in order: multi-token aliases come before their single-token prefixes. */
    private static final List<Rule> RULES = List.of(
            new Rule("UBER EATS", "Uber Eats", "Food & Dining"),
            new Rule("AMAZON", "Amazon", "Shopping"),
            new Rule("AMZN", "Amazon", "Shopping"),
            new Rule("FLIPKART", "Flipkart", "Shopping"),
            new Rule("MYNTRA", "Myntra", "Shopping"),
            new Rule("SWIGGY", "Swiggy", "Food & Dining"),
            new Rule("ZOMATO", "Zomato", "Food & Dining"),
            new Rule("DOMINOS", "Domino's", "Food & Dining"),
            new Rule("STARBUCKS", "Starbucks", "Food & Dining"),
            new Rule("BIGBASKET", "BigBasket", "Groceries"),
            new Rule("BLINKIT", "Blinkit", "Groceries"),
            new Rule("ZEPTO", "Zepto", "Groceries"),
            new Rule("UBER", "Uber", "Transport"),
            new Rule("OLA", "Ola", "Transport"),
            new Rule("IRCTC", "IRCTC", "Travel"),
            new Rule("BOOKMYSHOW", "BookMyShow", "Entertainment"),
            new Rule("NETFLIX", "Netflix", "Subscriptions"),
            new Rule("SPOTIFY", "Spotify", "Subscriptions"),
            new Rule("AIRTEL", "Airtel", "Utilities"),
            new Rule("JIO", "Jio", "Utilities"));

    public MerchantMatch normalize(String rawDescription) {
        List<String> tokens = tokens(rawDescription);
        String normalized = tokens.isEmpty()
                ? rawDescription.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT)
                : String.join(" ", tokens);
        for (Rule rule : RULES) {
            if (Collections.indexOfSubList(tokens, rule.tokens()) >= 0) { // whole tokens only
                return new MerchantMatch(normalized, rule.canonicalName(), RULE_CONFIDENCE, rule.categoryHint());
            }
        }
        return new MerchantMatch(normalized, null, null, null);
    }

    private static List<String> tokens(String raw) {
        String s = UPI_HANDLE.matcher(raw.toUpperCase(Locale.ROOT)).replaceAll(" ");
        List<String> out = new ArrayList<>();
        for (String t : NON_WORD.split(s)) {
            if (!t.isEmpty() && !NOISE.contains(t) && !IFSC.matcher(t).matches() && !REFERENCE.matcher(t).matches()) {
                out.add(t);
            }
        }
        return out;
    }

}
