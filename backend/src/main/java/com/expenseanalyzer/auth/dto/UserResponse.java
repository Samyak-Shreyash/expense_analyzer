package com.expenseanalyzer.auth.dto;

import com.expenseanalyzer.user.model.UserRole;

import java.util.UUID;

/**
 * DTO for user information in authentication responses.
 * Contains only public user fields (no password or sensitive data).
 */
public record UserResponse(
    String email,
    String fullName,
    UserRole role
) {}
