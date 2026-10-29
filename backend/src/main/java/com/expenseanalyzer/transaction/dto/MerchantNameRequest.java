package com.expenseanalyzer.transaction.dto;

/** Body for naming the merchant of an unmatched payment; {@code suggest} opts in to sharing the name (default off). */
public record MerchantNameRequest(String name, Boolean suggest) {

    public boolean shouldSuggest() {
        return Boolean.TRUE.equals(suggest);
    }
}
