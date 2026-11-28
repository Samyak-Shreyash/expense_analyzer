package com.expenseanalyzer.statement.dto;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementSource;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.user.model.UserAccount;
import java.time.Instant;
import java.util.UUID;

/**
 * Owner-facing statement view. Storage key and file hash stay server-side; account numbers are masked.
 * {@code fileKept} is false once the original was deleted after import.
 */
public record StatementResponse(UUID id, String fileName, StatementFormat format, StatementSource source,
                                StatementStatus status,
                                String processingError, Instant createdAt, Instant completedAt,
                                String accountNumber, String bankName, String detectedAccount, boolean fileKept) {

    public static StatementResponse from(Statement s) {
        return new StatementResponse(s.getId(), s.getFileName(), s.getFormat(), s.getSource(), s.getStatus(),
                s.getProcessingError(), s.getCreatedAt(), s.getCompletedAt(), UserAccount.mask(s.getAccountNumber()),
                s.getBankName(), s.getDetectedAccount(), s.getStorageKey() != null);
    }
}
