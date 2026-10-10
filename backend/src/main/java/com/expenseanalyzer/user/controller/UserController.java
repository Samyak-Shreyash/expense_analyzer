package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.dto.UpdateUserRequest;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.auth.dto.UserResponse;
import com.expenseanalyzer.user.service.UserService;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserController {

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
            @RequestBody UpdateUserRequest request) {
        try {
            return ResponseEntity.ok(userService.updateUser(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(null);
        }
    }
}
