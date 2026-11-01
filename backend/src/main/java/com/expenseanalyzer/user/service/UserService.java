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

import java.util.LinkedHashMap;
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

        return applyUpdate(optionalUser.get(), callerEmail, request);
    }

    /**
     * Update the authenticated caller's own details.
     */
    @Transactional
    public UserResponse updateMe(String callerEmail, UserRequest request) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return applyUpdate(user, callerEmail, request);
    }

    private UserResponse applyUpdate(User updatedUser, String callerEmail, UserRequest request) {
        // Ownership: another user's id looks like a missing one
        if (!updatedUser.getEmail().equals(callerEmail)) {
            throw new IllegalArgumentException("User not found");
        }

        // Callers may not change their own role or status
        if (request.role() != null || request.active() != null) {
            throw new AccessDeniedException("Not allowed to update this user");
        }

        // Email change is not supported for now
        if (request.email() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be changed");
        }

        // Update full name if provided
        if (request.fullName() != null) {
            if (request.fullName().isBlank() || request.fullName().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid full name");
            }
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
                Optional<PreferenceRepository.View> existing =
                        preferenceRepository.findViewByUserIdAndKey(updatedUser.getId(), entry.getKey());
                Theme theme = existing.map(PreferenceRepository.View::getTheme).orElse(null);
                Currency currency = existing.map(PreferenceRepository.View::getCurrency).orElse(null);
                NotificationMode mode = existing.map(PreferenceRepository.View::getNotificationMode).orElse(null);
                Object value = entry.getValue();
                try {
                    switch (entry.getKey()) {
                        case "theme" -> theme = Theme.valueOf(String.valueOf(value).toUpperCase());
                        case "currency" -> currency = Currency.valueOf(String.valueOf(value).toUpperCase());
                        case "notifications" -> {
                            if (!(value instanceof Boolean on)) {
                                throw new IllegalArgumentException();
                            }
                            mode = on ? NotificationMode.ALL : NotificationMode.NONE;
                        }
                        default -> throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference: " + entry.getKey());
                }
                if (existing.isPresent()) {
                    preferenceRepository.updateTyped(updatedUser.getId(), entry.getKey(), theme, currency, mode);
                } else {
                    Preference pref = new Preference();
                    pref.setUserId(updatedUser.getId());
                    pref.setKey(entry.getKey());
                    pref.setTheme(theme);
                    pref.setCurrency(currency);
                    pref.setNotificationMode(mode);
                    preferenceRepository.save(pref);
                }
            }
        }

        return toResponse(updatedUser);
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

        return Optional.of(toResponse(user));
    }

    private UserResponse toResponse(User user) {
        Map<String, Object> prefs = new LinkedHashMap<>();
        for (PreferenceRepository.View p : preferenceRepository.findViewsByUserId(user.getId())) {
            switch (p.getKey()) {
                case "theme" -> prefs.put("theme", p.getTheme().name().toLowerCase());
                case "currency" -> prefs.put("currency", p.getCurrency().name().toLowerCase());
                case "notifications" -> prefs.put("notifications", p.getNotificationMode() != NotificationMode.NONE);
                default -> { }
            }
        }
        return new UserResponse(user.getEmail(), user.getFullName(), user.getRole(), prefs);
    }
}
