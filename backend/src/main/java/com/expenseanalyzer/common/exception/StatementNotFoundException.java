package com.expenseanalyzer.common.exception;

/** No statement with the id exists for the caller (also used when someone else owns it). */
public class StatementNotFoundException extends RuntimeException {

    public StatementNotFoundException() {
        super("Statement not found");
    }
}
