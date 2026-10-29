package com.expenseanalyzer.statement.service;

import com.expenseanalyzer.common.exception.InvalidFileException;
import com.expenseanalyzer.common.exception.InvalidFileException.Reason;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Validates an uploaded statement file by size, extension, MIME type and leading bytes. Never logs the filename. */
@Service
public class StatementFileValidator {

    private static final Logger log = LoggerFactory.getLogger(StatementFileValidator.class);

    private static final Map<String, Set<String>> ALLOWED_MIME = Map.of(
            "pdf", Set.of("application/pdf"),
            "xls", Set.of("application/vnd.ms-excel"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain"));

    private final long maxSizeBytes;

    public StatementFileValidator(@Value("${app.statement.upload.max-size-bytes:20971520}") long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    /** @param head first up to 8192 bytes of the file, read by the caller */
    public void validate(String filename, String contentType, long size, byte[] head) {
        if (size <= 0) {
            reject(Reason.EMPTY_FILE, "File is empty");
        }
        if (size > maxSizeBytes) {
            reject(Reason.FILE_TOO_LARGE, "File exceeds the maximum allowed size");
        }
        String ext = extension(filename);
        Set<String> mimes = ext == null ? null : ALLOWED_MIME.get(ext);
        if (mimes == null) {
            reject(Reason.UNSUPPORTED_EXTENSION, "Unsupported file extension");
        }
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mimes.contains(mime)) {
            reject(Reason.MIME_TYPE_MISMATCH, "File content type does not match its extension");
        }
        boolean ok = switch (ext) {
            case "pdf" -> startsWith(head, '%', 'P', 'D', 'F', '-');
            case "xls" -> startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1); // OLE2
            case "xlsx" -> startsWith(head, 0x50, 0x4B, 0x03, 0x04);
            default -> isUtf8Text(head);
        };
        if (!ok) {
            reject(Reason.SIGNATURE_MISMATCH, "File content does not match its extension");
        }
    }

    private void reject(Reason reason, String message) {
        log.warn("Statement upload rejected: reason={}", reason);
        throw new InvalidFileException(reason, message);
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] head, int... sig) {
        if (head == null || head.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (head[i] != (byte) sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtf8Text(byte[] head) {
        if (head == null) {
            return false;
        }
        for (byte x : head) {
            if (x == 0) {
                return false;
            }
        }
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        // endOfInput=false tolerates a multi-byte char cut at the buffer end
        CoderResult r = dec.decode(ByteBuffer.wrap(head), CharBuffer.allocate(head.length + 1), false);
        return !r.isError();
    }
}
