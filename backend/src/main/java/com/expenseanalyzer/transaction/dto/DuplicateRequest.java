package com.expenseanalyzer.transaction.dto;

/** Marks ({@code duplicate} true) or unmarks a transaction as a duplicate of another row the caller already has. */
public record DuplicateRequest(boolean duplicate) {
}
