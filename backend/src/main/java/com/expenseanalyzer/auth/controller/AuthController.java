package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.*;
import com.expenseanalyzer.user.model.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider; // Your JWT utility service

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        if (authentication.isAuthenticated() && !authentication.getAuthorities().isEmpty()) {
            String jwt = tokenProvider.generateToken(authentication);

            // Get user from Spring Security context to populate UserResponse DTO
            UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;
            Object principal = token.getPrincipal();
            if (principal instanceof User user) {
                return ResponseEntity.ok(new AuthResponse(jwt, new UserResponse(
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole()
                )));
            }
        }

        return ResponseEntity.status(401).build();
    }

    
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