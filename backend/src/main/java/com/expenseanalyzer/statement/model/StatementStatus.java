package com.expenseanalyzer.statement.model;

/** Processing lifecycle of an uploaded statement (LLD section 7). */
public enum StatementStatus {
    UPLOADED, QUEUED, PROCESSING, COMPLETED, FAILED
}
