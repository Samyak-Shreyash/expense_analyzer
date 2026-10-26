package com.expenseanalyzer.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFile(InvalidFileException e) {
        HttpStatus s = e.getReason() == InvalidFileException.Reason.FILE_TOO_LARGE
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(s, e.getMessage());
        p.setTitle("Invalid file");
        p.setProperty("reason", e.getReason().name());
        return ResponseEntity.status(s).body(p);
    }

    /** The servlet multipart limit was hit before the validator ran; same body as the validator's 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return handleInvalidFile(new InvalidFileException(
                InvalidFileException.Reason.FILE_TOO_LARGE, "File exceeds the maximum allowed size"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, "Missing file", "STATEMENT_FILE_MISSING",
                "Request must include a multipart part named file");
    }

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateStatement(DuplicateStatementException e) {
        ResponseEntity<ProblemDetail> res = problem(HttpStatus.CONFLICT, "Duplicate statement",
                "STATEMENT_DUPLICATE", e.getMessage());
        res.getBody().setProperty("id", e.getExistingId());
        return res;
    }

    @ExceptionHandler(StatementNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleStatementNotFound(StatementNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Statement not found", "STATEMENT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(StatementStorageException.class)
    public ResponseEntity<ProblemDetail> handleStatementStorage(StatementStorageException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable", "STATEMENT_STORAGE_UNAVAILABLE",
                "Statement storage is temporarily unavailable");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus s, String title, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(s, detail);
        p.setTitle(title);
        p.setProperty("code", code);
        return ResponseEntity.status(s).body(p);
    }
}
