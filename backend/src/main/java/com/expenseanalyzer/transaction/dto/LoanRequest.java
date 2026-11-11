package com.expenseanalyzer.transaction.dto;

import java.util.UUID;

/** Links an EMI to a loan (a CREDIT categorised Loan); a null {@code loanId} unlinks it. */
public record LoanRequest(UUID loanId) {
}
