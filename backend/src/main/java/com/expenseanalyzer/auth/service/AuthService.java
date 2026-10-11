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

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for user registration and authentication operations.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;

    // Singleton flag: true if admin user exists (checked once at startup)
    private boolean hasAdminUser = false;

    public AuthService(UserRepository userRepository, PreferenceRepository preferenceRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        // Check if admin user exists at startup (Spring beans are singletons by default)
        hasAdminUser = userRepository.countByRole(UserRole.ADMIN) > 0;
    }

    /**
     * Register a new user.
     * Validates email uniqueness and creates user with hashed password.
     */
    public User register(RegisterRequest request) {
        // Validate email is not null
        if (request.email() == null) {
            throw new IllegalArgumentException("Email is required");
        }

        // Validate password is not null
        if (request.password() == null) {
            throw new IllegalArgumentException("Password is required");
        }

        // Validate password length >= 8
        if (request.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Create new user - fullName not in RegisterRequest, leave as empty string
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName("");

        // Assign ADMIN role if no admin users exist, otherwise USER
        if (!hasAdminUser) {
            user.setRole(UserRole.ADMIN);
        } else {
            user.setRole(UserRole.USER);
        }

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

        return userRepository.save(user);
    }

    /**
     * Check if email is already registered.
     */
    public boolean isEmailRegistered(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
