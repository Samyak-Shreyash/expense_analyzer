package com.expenseanalyzer.statement.dto;

import com.expenseanalyzer.statement.model.StatementStatus;
import java.util.UUID;

public record StatementUploadResponse(UUID id, StatementStatus status, String fileName) {
}
