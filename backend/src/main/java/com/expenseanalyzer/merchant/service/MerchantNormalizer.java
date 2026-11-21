package com.expenseanalyzer.merchant.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static final int RULES_VERSION = 3;
    public static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.95");

    private static final Pattern UPI_HANDLE = Pattern.compile("[A-Z0-9._-]+@[A-Z]+");
    private static final Pattern NON_WORD = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern TRAILING_DIGITS = Pattern.compile("\\d+$");
    private static final Pattern IFSC = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");
    private static final Pattern REFERENCE = Pattern.compile("(?=.*\\d)[A-Z0-9]{6,}|\\d+");

    /** Payment rails, bank shorthand and UPI app handles: they say how money moved, not to whom. */
    private static final Set<String> NOISE = Set.of(
            "UPI", "POS", "NEFT", "IMPS", "RTGS", "ACH", "ECS", "NACH", "D", "CR", "DR", "REF", "TXN",
            "INB", "MB", "IB", "VPS", "PAYTM", "OKAXIS", "OKHDFCBANK", "OKICICI", "OKSBI", "YBL", "IBL", "AXL");

    /** "<brand> PAYMENTS BANK" (or "<brand> BANK") is a bank, not the brand's merchant. */
    private static final Set<String> PAYMENTS = Set.of("PAYMENTS", "PAYMENT");

    private record Rule(List<String> tokens, String canonicalName, String categoryHint) {
        Rule(String alias, String canonicalName, String categoryHint) {
            this(Arrays.asList(alias.split(" ")), canonicalName, categoryHint);
        }
    }

    /** Checked in order: multi-token aliases come before their single-token prefixes. */
    private static final List<Rule> RULES = List.of(
            new Rule("UBER EATS", "Uber Eats", "Food & Dining"),
            new Rule("URBANCLAP", "Urban Company", "Home Services"),
            new Rule("URBAN COMPANY", "Urban Company", "Home Services"),
            new Rule("URBANCOMPANY", "Urban Company", "Home Services"),
            new Rule("KFC", "KFC", "Food & Dining"),
            new Rule("RAMESHWARAM", "The Rameshwaram Cafe", "Food & Dining"),
            new Rule("THE RAMES", "The Rameshwaram Cafe", "Food & Dining"),
            new Rule("SMARTQ", "SmartQ", "Food & Dining"),
            new Rule("THESMARTQ", "SmartQ", "Food & Dining"),
            new Rule("JUBILANT", "Domino's", "Food & Dining"),
            new Rule("DOMINO", "Domino's", "Food & Dining"),
            new Rule("INDIAN RAILWAYS", "IRCTC", "Travel"),
            new Rule("POLICYBAZAAR", "Policybazaar", "Insurance"),
            new Rule("EKART", "Ekart", "Shopping"),
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

    /** Distinct category names used by the rules, in rule order: the user's default categories. */
    public static List<String> defaultCategoryNames() {
        return RULES.stream().map(Rule::categoryHint).distinct().toList();
    }

    public MerchantMatch normalize(String rawDescription) {
        List<String> tokens = tokens(rawDescription);
        String normalized = tokens.isEmpty()
                ? rawDescription.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT)
                : String.join(" ", tokens);
        Rule rule = firstMatch(tokens);
        if (rule == null) {
            rule = firstMatch(digitStrippedTokens(rawDescription));
        }
        return rule == null
                ? new MerchantMatch(normalized, null, null, null)
                : new MerchantMatch(normalized, rule.canonicalName(), RULE_CONFIDENCE, rule.categoryHint());
    }

    private static Rule firstMatch(List<String> tokens) {
        for (Rule rule : RULES) {
            if (matches(tokens, rule.tokens())) {
                return rule;
            }
        }
        return null;
    }

    /** Second-pass tokens for matching only: references are kept, but trailing digits are dropped ("THESMARTQ1"). */
    private static List<String> digitStrippedTokens(String raw) {
        String s = UPI_HANDLE.matcher(raw.toUpperCase(Locale.ROOT)).replaceAll(" ");
        List<String> out = new ArrayList<>();
        for (String t : NON_WORD.split(s)) {
            String stripped = TRAILING_DIGITS.matcher(t).replaceFirst("");
            if (!stripped.isEmpty() && !NOISE.contains(t)) {
                out.add(stripped);
            }
        }
        return out;
    }

    /** Whole-token match, skipping a brand that names a bank ("AIRTEL PAYMENTS BANK" is the bank, not Airtel). */
    private static boolean matches(List<String> tokens, List<String> alias) {
        for (int i = 0; i + alias.size() <= tokens.size(); i++) {
            if (tokens.subList(i, i + alias.size()).equals(alias) && !namesABank(tokens, i + alias.size())) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesABank(List<String> tokens, int next) {
        return tokens.size() > next + 1 && PAYMENTS.contains(tokens.get(next)) && "BANK".equals(tokens.get(next + 1))
                || tokens.size() > next && "BANK".equals(tokens.get(next))
                || tokens.size() == next + 1 && isPaymentsPrefix(tokens.get(next));
    }

    /** A last token cut short by the statement's width: "PA", "PAY", ... "PAYMENTS". */
    private static boolean isPaymentsPrefix(String token) {
        return token.length() >= 2 && "PAYMENTS".startsWith(token);
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
