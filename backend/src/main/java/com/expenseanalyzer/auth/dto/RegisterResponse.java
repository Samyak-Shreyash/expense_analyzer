package com.expenseanalyzer.auth.dto;

import java.util.UUID;

/**
 * Response DTO for user registration.
 */
public class RegisterResponse {

    /**
     * User's UUID.
     */
    private final UUID id;

    /**
     * User's email address.
     */
    private final String email;

    /**
     * User's full name.
     */
    private final String fullName;

    public RegisterResponse(UUID id, String email, String fullName) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }
}
