package com.expenseanalyzer.user.dto;

import com.expenseanalyzer.user.model.UserRole;
import java.util.Map;

/**
 * DTO for updating user information.
 */
public record UserRequest(
    String email,
    String fullName,
    UserRole role,
    Boolean active,
    Map<String, Object> preferences
) {}
