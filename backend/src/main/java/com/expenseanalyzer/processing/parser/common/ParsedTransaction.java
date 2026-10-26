package com.expenseanalyzer.processing.parser.common;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(int rowNumber, LocalDate date, String rawDescription, BigDecimal amount, String type) {}
