package com.expenseanalyzer.transaction.dto;

import com.expenseanalyzer.transaction.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional list filters; dates are inclusive, amounts in currency units, q matches descriptions. {@code transfer} keeps
 * only own-account transfers (true) or only other transactions (false). {@code reversalPending} keeps only rows marked
 * as failed or refunded whose other half isn't paired yet (true), or only other rows (false). {@code duplicate} keeps
 * only rows marked as duplicates (true) or only other rows (false).
 */
public record TransactionFilter(String q, UUID statementId, LocalDate from, LocalDate to, UUID merchantId,
                                TransactionDirection direction, BigDecimal minAmount, BigDecimal maxAmount,
                                String category, Boolean transfer, UUID personId, Boolean reversalPending,
                                Boolean duplicate) {
}
