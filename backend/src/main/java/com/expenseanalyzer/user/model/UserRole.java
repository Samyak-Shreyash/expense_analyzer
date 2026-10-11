package com.expenseanalyzer.user.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum UserRole {
    USER,
    ADMIN;

    private static final Logger logger = LoggerFactory.getLogger(UserRole.class);

    public static UserRole from(String value) {
        return switch (value.toUpperCase()) {
            case "USER" -> USER;
            case "ADMIN" -> ADMIN;
            default -> {
                logger.warn("Invalid role provided: {}. Valid roles: USER, ADMIN", value);
                throw new IllegalArgumentException("Invalid role: " + value);
            }
        };
    }
}
