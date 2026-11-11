package com.expenseanalyzer.transaction.dto;

/** Closes ({@code closed} true) or reopens a loan. */
public record LoanClosedRequest(boolean closed) {
}
