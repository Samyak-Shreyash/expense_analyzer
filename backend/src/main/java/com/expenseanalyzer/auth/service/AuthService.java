package com.expenseanalyzer.auth.service;

import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.Theme;
import com.expenseanalyzer.user.model.Currency;
import com.expenseanalyzer.user.model.NotificationMode;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * Service for user registration and authentication operations.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_PASSWORD_BYTES = 72;
    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PreferenceRepository preferenceRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user.
     * Validates email uniqueness and creates user with hashed password.
     */
    @Transactional
    public User register(RegisterRequest request) {
        logger.debug("Registering user");

        // Validate email is not null
        if (request.email() == null) {
            logger.warn("Registration failed: Email is required");
            throw new IllegalArgumentException("Email is required");
        }

        // Validate password is not null
        if (request.password() == null) {
            logger.warn("Registration failed: Password is required");
            throw new IllegalArgumentException("Password is required");
        }

        // Validate password length >= 8
        if (request.password().length() < 8) {
            logger.warn("Registration failed: Password must be at least 8 characters. Request password length: {}", request.password().length());
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        // BCrypt limit
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.email()).isPresent()) {
            logger.warn("Registration failed: Email already registered");
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.setFullName("");

        // Registration never grants ADMIN: on a fresh deploy the first visitor could be anyone
        user.setRole(UserRole.USER);

        // Save first: preferences reference the generated user id
        user = userRepository.save(user);

        // Initialize preferences with defaults (stored in separate preferences table)
        Preference currencyPref = new Preference();
        currencyPref.setUserId(user.getId());
        currencyPref.setKey("currency");
        currencyPref.setCurrency(Currency.INR);
        preferenceRepository.save(currencyPref);

        Preference themePref = new Preference();
        themePref.setUserId(user.getId());
        themePref.setKey("theme");
        themePref.setTheme(Theme.DARK);
        preferenceRepository.save(themePref);

        Preference notificationPref = new Preference();
        notificationPref.setUserId(user.getId());
        notificationPref.setKey("notifications");
        notificationPref.setNotificationMode(NotificationMode.ALL);
        preferenceRepository.save(notificationPref);

        return user;
    }
}
