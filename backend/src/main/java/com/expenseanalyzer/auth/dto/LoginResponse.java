package com.expenseanalyzer.auth.dto;

import java.util.UUID;

/**
 * Response DTO for user login.
 */
public class LoginResponse {

    /**
     * JWT token for authentication.
     */
    private final String jwtToken;

    /**
     * User's UUID.
     */
    private final UUID id;

    /**
     * User's email address.
     */
    private final String email;

    public LoginResponse(String jwtToken, UUID id, String email) {
        this.jwtToken = jwtToken;
        this.id = id;
        this.email = email;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
