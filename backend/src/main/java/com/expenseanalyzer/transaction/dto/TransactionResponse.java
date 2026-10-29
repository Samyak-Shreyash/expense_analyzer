package com.expenseanalyzer.transaction.dto;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Owner-facing transaction. Amount is in currency units (rupees), always positive; direction gives the sign.
 * {@code category} is the effective category name ("Uncategorized" when none); {@code categoryOverridden} is true
 * when the transaction carries its own category.
 */
public record TransactionResponse(UUID id, LocalDate date, String description, String normalizedDescription,
                                  BigDecimal amount, String currency, String direction, UUID merchantId,
                                  String merchantName, UUID categoryId, String category, boolean categoryOverridden,
                                  UUID statementId, UUID personId, String personName,
                                  String suggestedMerchantName) {

    /**
     * @param merchant  the transaction's merchant, or null when it has none
     * @param person    the transaction's person, or null when it has none
     * @param effective the transaction's effective category, or null for Uncategorized
     * @param suggested merchant name other users suggest for this unmatched payee, or null
     */
    public static TransactionResponse from(Transaction t, Merchant merchant, Person person, Category effective,
                                           String suggested) {
        return new TransactionResponse(t.getId(), LocalDate.ofInstant(t.getDate(), ZoneOffset.UTC),
                t.getRawDescription(), t.getNormalizedDescription(), t.getAmount(), t.getCurrency(), t.getType(),
                t.getMerchantId(), merchant == null ? null : merchant.getCanonicalName(),
                effective == null ? null : effective.getId(),
                effective == null ? SpendingAnalyticsService.UNCATEGORIZED : effective.getName(),
                t.getCategoryId() != null, t.getStatementId(), t.getPersonId(),
                person == null ? null : person.getName(), suggested);
    }
}
