package com.expenseanalyzer.statement.dto;

/** Fields left null are unchanged; a blank account number clears it. */
public record StatementUpdateRequest(String fileName, String accountNumber, String bankName) {
}
