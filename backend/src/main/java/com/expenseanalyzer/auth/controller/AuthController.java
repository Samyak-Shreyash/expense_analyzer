package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.*;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuthService authService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

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

            // Get email from Spring Security principal
            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                return ResponseEntity.ok(new AuthResponse(jwt));
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
        try {
            // Call AuthService to create the user and save to database
            User user = authService.register(request);

            return ResponseEntity.ok("User registered successfully");
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Registration failed");
        }
    }

    /**
     * Check if email is already registered.
     * GET /api/auth/register/check-email
     */
    @GetMapping("/register/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam String email) {
        logger.debug("Checking if email is registered: {}", email);
        try {
            if (authService.isEmailRegistered(email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered");
            }
        } catch (Exception e) {
            logger.error("Error checking email registration for: {}", email, e);
            // Ignore exceptions and return default response
        }

        return ResponseEntity.ok("Email not found");
    }

}