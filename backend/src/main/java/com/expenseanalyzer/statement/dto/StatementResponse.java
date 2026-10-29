package com.expenseanalyzer.statement.dto;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import java.time.Instant;
import java.util.UUID;

/** Owner-facing statement view. Storage key and file hash stay server-side. */
public record StatementResponse(UUID id, String fileName, StatementFormat format, StatementStatus status,
                                String processingError, Instant createdAt, Instant completedAt) {

    public static StatementResponse from(Statement s) {
        return new StatementResponse(s.getId(), s.getFileName(), s.getFormat(), s.getStatus(),
                s.getProcessingError(), s.getCreatedAt(), s.getCompletedAt());
    }
}
