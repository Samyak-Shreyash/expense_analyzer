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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
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
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @RequestBody UserRequest request) {
        logger.debug("Updating user: {}", userId);
        try {
            return ResponseEntity.ok(userService.updateUser(jwt.getSubject(), userId, request));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(404).body(null);
        }
    }

    /**
     * Get current authenticated user.
     * GET /api/user/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        logger.debug("Getting current user");
        return ResponseEntity.of(userService.getCurrentUser(jwt.getSubject()));
    }

}
