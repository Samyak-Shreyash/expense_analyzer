package com.expenseanalyzer.auth.dto;

import com.expenseanalyzer.user.model.UserRole;
import java.util.Map;

/**
 * DTO for updating user information.
 */
public record UpdateUserRequest(
    String email,
    String fullName,
    UserRole role,
    Boolean active,
    Map<String, Object> preferences
) {}
