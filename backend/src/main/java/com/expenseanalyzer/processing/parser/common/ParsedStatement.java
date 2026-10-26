package com.expenseanalyzer.processing.parser.common;

import java.util.List;

public record ParsedStatement(List<ParsedTransaction> transactions, List<RowError> errors) {

    public static ParsedStatement failed(RowError error) {
        return new ParsedStatement(List.of(), List.of(error));
    }
}
