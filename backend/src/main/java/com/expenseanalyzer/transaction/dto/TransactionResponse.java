package com.expenseanalyzer.transaction.dto;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.service.PayeeKey;
import com.expenseanalyzer.merchant.service.PersonNameParser;
import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Owner-facing transaction. Amount is in currency units (rupees), always positive; direction gives the sign.
 * {@code category} is the effective category name ("Uncategorized" when none); {@code categoryOverridden} is true
 * when the transaction carries its own category. {@code payeeName} is the UPI payee from the description (the P2M
 * merchant or the P2A/P2P person), set while the transaction has neither a merchant nor a person; null otherwise.
 * {@code payeeType} says which it is (MERCHANT or PERSON), null with no payeeName. {@code transfer} marks money moved
 * between the caller's own accounts; {@code transferPairId} is its other half, if paired.
 */
public record TransactionResponse(UUID id, LocalDate date, String description, String normalizedDescription,
                                  BigDecimal amount, String currency, String direction, UUID merchantId,
                                  String merchantName, UUID categoryId, String category, boolean categoryOverridden,
                                  UUID statementId, UUID personId, String personName,
                                  String suggestedMerchantName, String payeeName, String payeeType,
                                  boolean transfer, UUID transferPairId) {

    /**
     * @param merchant  the transaction's merchant, or null when it has none
     * @param ownName   the caller's own spelling of the merchant (from their alias), or null to use the canonical name
     * @param person    the transaction's person, or null when it has none
     * @param effective the transaction's effective category, or null for Uncategorized
     * @param suggested merchant name other users suggest for this unmatched payee, or null
     */
    public static TransactionResponse from(Transaction t, Merchant merchant, String ownName, Person person, Category effective,
                                           String suggested) {
        Payee payee = payee(t);
        return new TransactionResponse(t.getId(), LocalDate.ofInstant(t.getDate(), ZoneOffset.UTC),
                t.getRawDescription(), t.getNormalizedDescription(), t.getAmount(), t.getCurrency(), t.getType(),
                t.getMerchantId(), ownName != null ? ownName : merchant == null ? null : merchant.getCanonicalName(),
                effective == null ? null : effective.getId(),
                effective == null ? SpendingAnalyticsService.UNCATEGORIZED : effective.getName(),
                t.getCategoryId() != null, t.getStatementId(), t.getPersonId(),
                person == null ? null : person.getName(), suggested, payee == null ? null : payee.name(),
                payee == null ? null : payee.type(), t.isTransfer(), t.getTransferPairId());
    }

    private record Payee(String name, String type) {
    }

    /** The UPI payee of a transaction with no merchant or person, else null. */
    private static Payee payee(Transaction t) {
        if (t.getMerchantId() != null || t.getPersonId() != null) {
            return null;
        }
        String raw = t.getRawDescription();
        return PayeeKey.p2mName(raw).map(n -> new Payee(n, "MERCHANT"))
                .or(() -> PersonNameParser.parse(raw).map(n -> new Payee(n, "PERSON"))).orElse(null);
    }
}
