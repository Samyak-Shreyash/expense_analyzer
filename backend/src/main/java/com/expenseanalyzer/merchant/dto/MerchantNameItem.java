package com.expenseanalyzer.merchant.dto;

import java.util.UUID;

/**
 * One of the caller's own merchant names. {@code payee} is the caller's own payee key; {@code shared} is true while a
 * suggestion vote exists; {@code reported} is true when anyone reported this name for this payee (no counts or
 * reporters are ever exposed).
 */
public record MerchantNameItem(UUID id, String name, String payee, long transactionCount, boolean shared,
                               boolean optedOut, boolean shareable, String notShareableReason, boolean reported) {
}
