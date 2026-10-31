package com.expenseanalyzer.transaction.dto;

import java.util.UUID;

/**
 * Marks ({@code transfer} true) or unmarks a transaction as money moved between the caller's own accounts.
 * {@code pairWithId} (optional, marking only) names the other half; without it the transaction is a single transfer.
 */
public record TransferRequest(boolean transfer, UUID pairWithId) {
}
