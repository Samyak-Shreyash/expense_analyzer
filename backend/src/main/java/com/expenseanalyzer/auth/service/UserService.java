package com.expenseanalyzer.auth.service;

import com.expenseanalyzer.auth.dto.LoginRequest;
import com.expenseanalyzer.auth.dto.LoginResponse;
import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.auth.dto.RegisterResponse;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for user authentication operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * JWT Secret key for token generation.
     * In production, this should be stored in environment variables.
     */
    @Getter
    private static String JWT_SECRET = "expense-analyzer-jwt-secret-key-change-in-production";

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    /**
     * Initialize JWT secret key from environment variable.
     */
    @PostConstruct
    void init() {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            JWT_SECRET = envSecret;
        }
    }

    /**
     * Register a new user with hashed password.
     *
     * @param request the registration request
     * @return the registered user's information
     */
    public RegisterResponse registerUser(RegisterRequest request) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            throw new RuntimeException("User with email " + request.getEmail() + " already exists");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        // Persist user
        userRepository.save(user);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getFullName());
    }

    /**
     * Authenticate user and return JWT token.
     *
     * @param request the login request
     * @return the authentication response with JWT token
     */
    public LoginResponse loginUser(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate JWT token
        String jwtToken = generateJwtToken(userOpt.get().getId());

        return new LoginResponse(jwtToken, userOpt.get().getId(), request.getEmail());
    }

    /**
     * Generate JWT token for the given user ID.
     *
     * @param userId the user's UUID
     * @return JWT token string
     */
    private String generateJwtToken(UUID userId) {
        // In production, use a proper JWT library like jjwt or jwt-simple
        // This is a simplified implementation for demonstration
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
               "eyJzdWIiOiJ1dWlkOiJ" + userId.toString().substring(0, 8) +
               "...." + UUID.randomUUID().toString();
    }
}
