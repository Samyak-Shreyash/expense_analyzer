package com.expenseanalyzer.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.expenseanalyzer.common.exception.InvalidFileException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void fileTooLargeMapsTo413() {
        var res = handler.handleInvalidFile(new InvalidFileException(Reason.FILE_TOO_LARGE, "msg"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody().getDetail()).isEqualTo("msg");
        assertThat(res.getBody().getProperties()).containsEntry("reason", "FILE_TOO_LARGE");
    }

    @Test
    void otherReasonsMapTo400() {
        var res = handler.handleInvalidFile(new InvalidFileException(Reason.UNSUPPORTED_EXTENSION, "msg"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
