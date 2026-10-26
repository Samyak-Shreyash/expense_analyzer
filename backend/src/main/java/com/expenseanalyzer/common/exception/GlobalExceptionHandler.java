package com.expenseanalyzer.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
