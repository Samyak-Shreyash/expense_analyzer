package com.expenseanalyzer.statement.dto;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementStatus;
import java.time.Instant;
import java.util.UUID;

public record StatementResponse(UUID id, StatementStatus status, String fileName, String contentType,
                                Long sizeBytes, Instant createdAt) {

    public static StatementResponse from(Statement s) {
        return new StatementResponse(s.getId(), s.getStatus(), s.getOriginalFilename(), s.getContentType(),
                s.getSizeBytes(), s.getCreatedAt());
    }
}
