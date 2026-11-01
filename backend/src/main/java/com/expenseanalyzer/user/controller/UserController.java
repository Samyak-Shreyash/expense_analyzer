package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.dto.ChangePasswordRequest;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.auth.service.PasswordService;
import com.expenseanalyzer.user.dto.DeleteAccountRequest;
import com.expenseanalyzer.user.dto.UserRequest;
import com.expenseanalyzer.user.dto.UserResponse;
import com.expenseanalyzer.user.service.AccountDeletionService;
import com.expenseanalyzer.user.service.UserService;

import jakarta.validation.Valid;
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
    private final PasswordService passwordService;
    private final AccountDeletionService accountDeletionService;


    /**
     * Update the authenticated user's own details.
     * PUT /api/user/me
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UserRequest request) {
        try {
            return ResponseEntity.ok(userService.updateMe(jwt.getSubject(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(null);
        }
    }

    /**
     * Change the authenticated user's password.
     * PUT /api/user/me/password
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete the authenticated user's account and all their financial data.
     * DELETE /api/user/me
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(jwt.getSubject(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Update user information.
     * PUT /api/user/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @RequestBody UserRequest request) {
        try {
            return ResponseEntity.ok(userService.updateUser(jwt.getSubject(), userId, request));
        } catch (IllegalArgumentException e) {
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
