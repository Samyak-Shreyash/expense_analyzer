package com.expenseanalyzer.statement.dto;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementStatus;
import java.util.UUID;

public record StatementStatusResponse(UUID id, StatementStatus status, String processingError) {

    public static StatementStatusResponse from(Statement s) {
        return new StatementStatusResponse(s.getId(), s.getStatus(), s.getProcessingError());
    }
}
