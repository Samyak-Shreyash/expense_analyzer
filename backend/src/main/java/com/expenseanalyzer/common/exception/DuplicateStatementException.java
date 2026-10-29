package com.expenseanalyzer.common.exception;

import java.util.UUID;

/** The caller already uploaded a file with identical content. */
public class DuplicateStatementException extends RuntimeException {

    private final UUID existingId;

    public DuplicateStatementException(UUID existingId) {
        super("This statement has already been uploaded");
        this.existingId = existingId;
    }

    public UUID getExistingId() {
        return existingId;
    }
}
