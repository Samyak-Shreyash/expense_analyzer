package com.expenseanalyzer.transaction.dto;

import java.util.UUID;

/**
 * Pairs a failed payment with the refund that reversed it ({@code pairWithId} names the other half), or unpairs it
 * when {@code pairWithId} is null.
 */
public record ReversalRequest(UUID pairWithId) {
}
