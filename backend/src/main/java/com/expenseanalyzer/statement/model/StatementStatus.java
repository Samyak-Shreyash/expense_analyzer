package com.expenseanalyzer.statement.model;

/** Processing state of an uploaded statement. */
public enum StatementStatus {
    QUEUED,
    PROCESSING,
    PROCESSED,
    FAILED
}
