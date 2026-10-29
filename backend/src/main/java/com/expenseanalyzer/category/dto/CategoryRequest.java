package com.expenseanalyzer.category.dto;

/** Create/update body; {@code countsAsSpending} defaults to true when omitted. */
public record CategoryRequest(String name, Boolean countsAsSpending) {}
