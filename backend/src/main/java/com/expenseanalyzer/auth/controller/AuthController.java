package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.*;
import com.expenseanalyzer.auth.repository.UserRepository;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;


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

            // Extract email from Spring Security User principal
            String email = null;
            if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
                email = springUser.getUsername();
            }

            if (email != null) {
                // Fetch domain model User from repository using email
                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null) {
                    return ResponseEntity.ok(new AuthResponse(jwt, new UserResponse(
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole()
                    )));
                }
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
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    /**
     * Check if email is already registered.
     * GET /api/auth/register/check-email
     */
    @GetMapping("/register/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam String email) {
        try {
            if (authService.isEmailRegistered(email)) {
                return ResponseEntity.status(409).body("Email already registered");
            }
        } catch (Exception e) {
            // Ignore exceptions and return default response
        }

        return ResponseEntity.ok("Email not found");
    }

}