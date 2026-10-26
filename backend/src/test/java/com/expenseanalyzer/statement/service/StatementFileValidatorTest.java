package com.expenseanalyzer.statement.service;

import static com.expenseanalyzer.common.exception.InvalidFileException.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.expenseanalyzer.common.exception.InvalidFileException;
import com.expenseanalyzer.common.exception.InvalidFileException.Reason;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StatementFileValidatorTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final StatementFileValidator v = new StatementFileValidator(1024);

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private void assertRejects(Reason reason, String name, String type, long size, byte[] head) {
        InvalidFileException e = catchThrowableOfType(InvalidFileException.class, () -> v.validate(name, type, size, head));
        assertThat(e).isNotNull();
        assertThat(e.getReason()).isEqualTo(reason);
    }

    @Test
    void acceptsValidFiles() {
        assertThatCode(() -> {
            v.validate("a.pdf", "application/pdf", 100, b("%PDF-1.7"));
            v.validate("a.xlsx", XLSX, 100, new byte[] {0x50, 0x4B, 3, 4});
            v.validate("a.csv", "text/csv; charset=utf-8", 10, b("date,amount\n"));
            v.validate("A.PDF", "application/pdf", 100, b("%PDF-1.7"));
            v.validate("a.pdf", "application/pdf", 1024, b("%PDF-1.7"));
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooLarge() {
        assertRejects(FILE_TOO_LARGE, "a.pdf", "application/pdf", 1025, b("%PDF-"));
    }

    @Test
    void rejectsEmpty() {
        assertRejects(EMPTY_FILE, "a.pdf", "application/pdf", 0, new byte[0]);
    }

    @Test
    void rejectsBadExtension() {
        for (String n : new String[] {"a.exe", "a.csv.exe", "noext", null}) {
            assertRejects(UNSUPPORTED_EXTENSION, n, "application/pdf", 10, b("%PDF-"));
        }
    }

    @Test
    void rejectsMimeMismatch() {
        assertRejects(MIME_TYPE_MISMATCH, "a.pdf", "text/csv", 10, b("%PDF-"));
        assertRejects(MIME_TYPE_MISMATCH, "a.pdf", null, 10, b("%PDF-"));
    }

    @Test
    void rejectsBadSignature() {
        assertRejects(SIGNATURE_MISMATCH, "a.pdf", "application/pdf", 10, new byte[] {'M', 'Z', (byte) 0x90, 0});
        assertRejects(SIGNATURE_MISMATCH, "a.xlsx", XLSX, 10, b("%PDF-"));
        assertRejects(SIGNATURE_MISMATCH, "a.csv", "text/csv", 10, new byte[] {'a', 0, 'b'});
        assertRejects(SIGNATURE_MISMATCH, "a.csv", "text/csv", 10, new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFA});
    }

    @Test
    void messageHasNoFilename() {
        InvalidFileException e = catchThrowableOfType(InvalidFileException.class,
                () -> v.validate("secret-statement.pdf", "text/csv", 10, b("%PDF-")));
        assertThat(e.getMessage()).doesNotContain("secret-statement");
    }

    @Test
    void logsOneWarnWithoutFilename() {
        Logger logger = (Logger) LoggerFactory.getLogger(StatementFileValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertRejects(UNSUPPORTED_EXTENSION, "secret-name.exe", "application/pdf", 10, b("MZ"));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("UNSUPPORTED_EXTENSION").doesNotContain("secret-name");
    }
}
