package com.expenseanalyzer.merchant.service;

import java.math.BigDecimal;

/**
 * Result of normalising one raw description.
 *
 * @param normalizedDescription cleaned description, always present
 * @param canonicalName         canonical merchant, or null when no rule matched (never guessed)
 * @param confidence            0..1 confidence in {@code canonicalName}; null when there is none
 * @param categoryHint          coarse category suggestion from the rule; null when there is none
 */
public record MerchantMatch(String normalizedDescription, String canonicalName, BigDecimal confidence,
                            String categoryHint) {
}
