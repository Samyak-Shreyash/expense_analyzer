package com.expenseanalyzer.transaction.dto;

import java.util.UUID;

/**
 * Pairs a failed payment with the refund that reversed it ({@code pairWithId} names the other half), or unpairs it
 * when {@code pairWithId} is null. Unpaired, {@code pending} (absent means false) keeps it marked as failed until its other half is paired.
 */
public record ReversalRequest(UUID pairWithId, Boolean pending) {
}
