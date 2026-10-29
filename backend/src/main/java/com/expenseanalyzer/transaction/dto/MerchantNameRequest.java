package com.expenseanalyzer.transaction.dto;

/**
 * Body for naming the merchant of an unmatched payment. {@code share} (optional) applies only while the account shares
 * merchant names: missing or true shares this name, false opts it out.
 */
public record MerchantNameRequest(String name, Boolean share) {
}
