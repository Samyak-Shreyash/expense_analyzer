package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.service.AuditService;
import com.expenseanalyzer.auth.dto.*;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.auth.service.RefreshTokenService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    // Refresh cookie: HttpOnly (no JS access), sent only to /api/auth, never cross-site.
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            // Record the account id when the email exists; never the email itself.
            auditService.record(AuditAction.AUTH_LOGIN_FAILED,
                    userRepository.findByEmail(request.email()).map(User::getId).orElse(null));
            throw e;
        }
        if (authentication.isAuthenticated() && !authentication.getAuthorities().isEmpty()) {
            String jwt = tokenProvider.generateToken(authentication);
            
            // Get user from Spring Security context to populate UserResponse DTO
            UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;
            Object principal = token.getPrincipal();

            // Get email from Spring Security principal
            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                auditService.record(AuditAction.AUTH_LOGIN_SUCCEEDED, user.getId());
                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshTokenService.issue(user), refreshTokenService.getRefreshTokenSeconds()))
                        .body(new AuthResponse(jwt));
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
            auditService.record(AuditAction.AUTH_REGISTERED, user.getId());

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
        logger.debug("Checking if email is registered");
        try {
            if (authService.isEmailRegistered(email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered");
            }
        } catch (Exception e) {
            logger.error("Error checking email registration", e);
            // Ignore exceptions and return default response
        }

        return ResponseEntity.ok("Email not found");
    }

    /**
     * Exchange the refresh cookie for a new access token. Rotates the refresh token.
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return refreshTokenService.consume(refreshToken)
                .map(user -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshTokenService.issue(user), refreshTokenService.getRefreshTokenSeconds()))
                        .body(new AuthResponse(tokenProvider.generateToken(new UsernamePasswordAuthenticationToken(user.getEmail(), null)))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0))
                        .build());
    }

    /**
     * Revoke the refresh token and clear its cookie.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", 0)).build();
    }

    private static String refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true) // browsers accept Secure cookies on http://localhost
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
