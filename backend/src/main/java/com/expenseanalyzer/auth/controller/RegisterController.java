package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user registration endpoint.
 * Handles new user registration requests.
 */
@RestController
@RequestMapping("/api/auth")
public class RegisterController {

    /**
     * Register a new user.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        // TODO: Implement registration logic here
        // This should call AuthService to create the user and save to database

        return ResponseEntity.ok("User registered successfully");
    }

    /**
     * Check if email is already registered.
     * GET /api/auth/register/check-email
     */
    @GetMapping("/register/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam String email) {
        // TODO: Implement email validation logic here

        return ResponseEntity.ok("Email not found");
    }
}
