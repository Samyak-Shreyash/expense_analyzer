package com.expenseanalyzer.transaction.dto;

import com.expenseanalyzer.transaction.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Optional list filters; dates are inclusive, amounts in currency units. */
public record TransactionFilter(UUID statementId, LocalDate from, LocalDate to, UUID merchantId,
                                TransactionDirection direction, BigDecimal minAmount, BigDecimal maxAmount) {
}
