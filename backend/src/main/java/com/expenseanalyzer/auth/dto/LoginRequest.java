package com.expenseanalyzer.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user login.
 */
public class LoginRequest {

    /**
     * User's email address.
     */
    @NotBlank(message = "Email is required")
    private String email;

    /**
     * User's password.
     */
    @NotBlank(message = "Password is required")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
