package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.dto.UserRequest;
import com.expenseanalyzer.user.dto.UserResponse;
import com.expenseanalyzer.user.service.UserService;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final AuthService authService;
    private final UserService userService;


    /**
     * Update user information.
     * PUT /api/user/{userId}
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @RequestBody UserRequest request) {
        logger.debug("Updating user: {}", userId);
        try {
            return ResponseEntity.ok(userService.updateUser(userId, request));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(404).body(null);
        }
    }
}
