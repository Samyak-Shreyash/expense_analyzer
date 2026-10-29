package com.expenseanalyzer.statement.service;

import java.util.UUID;

/** The caller already uploaded an identical file. */
public class DuplicateStatementException extends RuntimeException {

    private final UUID statementId;

    public DuplicateStatementException(UUID statementId) {
        super("This file has already been uploaded");
        this.statementId = statementId;
    }

    public UUID getStatementId() {
        return statementId;
    }
}
