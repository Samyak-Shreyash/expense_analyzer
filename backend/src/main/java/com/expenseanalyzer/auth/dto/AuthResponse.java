package com.expenseanalyzer.auth.dto;

import com.expenseanalyzer.user.model.User;

/**
 * DTO for authentication response.
 * Returned by AuthController after successful login.
 * Contains JWT token and user information.
 */
public record AuthResponse(
    String token,
    User user
) {}
