package com.expenseanalyzer.transaction.dto;

import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Owner-facing transaction. Amount is in currency units (rupees), always positive; direction gives the sign. */
public record TransactionResponse(UUID id, LocalDate date, String description, String normalizedDescription,
                                  BigDecimal amount, String currency, String direction, UUID merchantId,
                                  String merchantName, String category, UUID statementId) {

    /** @param merchant the transaction's merchant, or null when it has none */
    public static TransactionResponse from(Transaction t, Merchant merchant) {
        return new TransactionResponse(t.getId(), LocalDate.ofInstant(t.getDate(), ZoneOffset.UTC),
                t.getRawDescription(), t.getNormalizedDescription(), t.getAmount(), t.getCurrency(), t.getType(),
                t.getMerchantId(), merchant == null ? null : merchant.getCanonicalName(),
                merchant == null ? null : merchant.getCategoryHint(), t.getStatementId());
    }
}
