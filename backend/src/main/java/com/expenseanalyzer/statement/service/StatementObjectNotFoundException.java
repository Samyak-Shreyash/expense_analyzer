package com.expenseanalyzer.statement.service;

public class StatementObjectNotFoundException extends RuntimeException {

    public StatementObjectNotFoundException() {
        super("Statement file not found in storage");
    }
}
