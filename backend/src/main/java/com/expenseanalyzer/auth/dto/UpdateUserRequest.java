package com.expenseanalyzer.auth.dto;

import com.expenseanalyzer.user.model.UserRole;

/**
 * DTO for updating user information.
 */
public record UpdateUserRequest(
    String email,
    String fullName,
    UserRole role,
    Boolean active
) {}
