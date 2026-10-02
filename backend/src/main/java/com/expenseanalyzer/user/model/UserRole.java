package com.expenseanalyzer.user.model;

public enum UserRole {
    USER,
    ADMIN;

    public static UserRole from(String value) {
        return switch (value.toUpperCase()) {
            case "USER" -> USER;
            case "ADMIN" -> ADMIN;
            default -> throw new IllegalArgumentException("Invalid role: " + value);
        };
    }
}
