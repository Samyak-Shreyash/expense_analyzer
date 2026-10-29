package com.expenseanalyzer.common.exception;

public class InvalidFileException extends RuntimeException {

    public enum Reason { EMPTY_FILE, FILE_TOO_LARGE, UNSUPPORTED_EXTENSION, MIME_TYPE_MISMATCH, SIGNATURE_MISMATCH,
        PASSWORD_REQUIRED, WRONG_PASSWORD }

    private final Reason reason;

    public InvalidFileException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
