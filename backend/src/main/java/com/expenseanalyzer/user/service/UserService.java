package com.expenseanalyzer.user.service;

import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.Theme;
import com.expenseanalyzer.user.dto.UserRequest;
import com.expenseanalyzer.user.dto.UserResponse;
import com.expenseanalyzer.user.model.Currency;
import com.expenseanalyzer.user.model.NotificationMode;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user operations.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;

    public UserService(UserRepository userRepository, PreferenceRepository preferenceRepository) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Update user information.
     */
    @Transactional
    public UserResponse updateUser(String callerEmail, UUID userId, UserRequest request) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (!optionalUser.isPresent()) {
            throw new IllegalArgumentException("User not found");
        }

        User updatedUser = optionalUser.get();

        // Ownership: callers may only update themselves, and may not change their own role or status
        if (!updatedUser.getEmail().equals(callerEmail) || request.role() != null || request.active() != null) {
            throw new AccessDeniedException("Not allowed to update this user");
        }

        // Email change is not supported for now
        if (request.email() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be changed");
        }

        // Update full name if provided
        if (request.fullName() != null && !request.fullName().isEmpty()) {
            updatedUser.setFullName(request.fullName());
        }

        // Update role if provided
        if (request.role() != null) {
            updatedUser.setRole(request.role());
        }

        // Update active status if provided
        if (request.active() != null) {
            updatedUser.setActive(request.active());
        }

        updatedUser = userRepository.save(updatedUser);

        // Update preferences if provided
        if (request.preferences() != null) {
            for (Map.Entry<String, Object> entry : request.preferences().entrySet()) {
                Preference pref = getPreference(updatedUser.getId(), entry.getKey());
                Object value = entry.getValue();
                try {
                    switch (entry.getKey()) {
                        case "theme" -> pref.setTheme(Theme.valueOf(String.valueOf(value).toUpperCase()));
                        case "currency" -> pref.setCurrency(Currency.valueOf(String.valueOf(value).toUpperCase()));
                        case "notifications" -> {
                            if (!(value instanceof Boolean on)) {
                                throw new IllegalArgumentException();
                            }
                            pref.setNotificationMode(on ? NotificationMode.ALL : NotificationMode.NONE);
                        }
                        default -> throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference: " + entry.getKey());
                }
                preferenceRepository.save(pref);
            }
        }

        return new UserResponse(
            updatedUser.getEmail(),
            updatedUser.getFullName(),
            updatedUser.getRole()
        );
    }

    /**
     * Get current authenticated user.
     */
    public Optional<UserResponse> getCurrentUser(String email) {
        logger.debug("Getting current user");
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        User user = optionalUser.get();

        return Optional.of(new UserResponse(
            user.getEmail(),
            user.getFullName(),
            user.getRole()
        ));
    }

    /**
     * Get preference by user ID and key.
     */
    private Preference getPreference(UUID userId, String key) {
        return preferenceRepository.findByUserIdAndKey(userId, key)
                .orElseGet(() -> {
                    Preference pref = new Preference();
                    pref.setUserId(userId);
                    pref.setKey(key);
                    return pref;
                });
    }
}
