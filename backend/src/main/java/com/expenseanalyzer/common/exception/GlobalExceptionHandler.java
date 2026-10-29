package com.expenseanalyzer.common.exception;

import com.expenseanalyzer.statement.service.DuplicateStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.core.exception.SdkException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFile(InvalidFileException e) {
        HttpStatus s = e.getReason() == InvalidFileException.Reason.FILE_TOO_LARGE
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(s, e.getMessage());
        p.setTitle("Invalid file");
        p.setProperty("reason", e.getReason().name());
        return ResponseEntity.status(s).body(p);
    }

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateStatement(DuplicateStatementException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Duplicate statement");
        p.setProperty("statementId", e.getStatementId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    /** Object storage unreachable or failing; detail stays generic. */
    @ExceptionHandler(SdkException.class)
    public ResponseEntity<ProblemDetail> handleStorageFailure(SdkException e) {
        log.warn("Object storage call failed: {}", e.getClass().getSimpleName());
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "File storage is temporarily unavailable");
        p.setTitle("Storage unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(p);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the maximum allowed size");
        p.setTitle("Invalid file");
        p.setProperty("reason", InvalidFileException.Reason.FILE_TOO_LARGE.name());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(p);
    }
}
