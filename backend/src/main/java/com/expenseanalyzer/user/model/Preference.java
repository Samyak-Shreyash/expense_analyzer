package com.expenseanalyzer.user.model;

public enum Preference {
    DARK,
    LIGHT;

    public static Preference from(String value) {
        return switch (value.toLowerCase()) {
            case "dark" -> DARK;
            case "light" -> LIGHT;
            default -> throw new IllegalArgumentException("Invalid preference: " + value);
        };
    }
}
