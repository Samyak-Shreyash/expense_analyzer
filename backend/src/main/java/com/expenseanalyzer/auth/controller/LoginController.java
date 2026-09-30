package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.AuthResponse;
import com.expenseanalyzer.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for login endpoint.
 * Handles authentication requests and returns JWT tokens.
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final AuthenticationManager authenticationManager;

    public LoginController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * Authenticate user and return JWT token.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        // Create Authentication object using UsernamePasswordAuthenticationToken
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            request.email(),
            request.password()
        );

        // Attempt authentication using credentials from request
        Authentication authenticated = authenticationManager.authenticate(authentication);

        // Set the authenticated user in security context
        SecurityContextHolder.getContext().setAuthentication(authenticated);

        // Generate JWT token (implementation depends on your JWT configuration)
        String token = generateToken(authenticated);

        return ResponseEntity.ok(new AuthResponse(token, null));
    }

    /**
     * Helper method to generate JWT token.
     * This is a placeholder - implement according to your JWT configuration.
     */
    private String generateToken(Authentication authentication) {
        // TODO: Implement JWT token generation based on your security config
        // For now, return a placeholder token
        return "placeholder-token-" + System.currentTimeMillis();
    }
}
