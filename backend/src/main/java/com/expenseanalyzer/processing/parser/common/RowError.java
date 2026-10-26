package com.expenseanalyzer.processing.parser.common;

/** field is a column name or null; never a cell value. */
public record RowError(int rowNumber, RowErrorCode code, String field) {}
