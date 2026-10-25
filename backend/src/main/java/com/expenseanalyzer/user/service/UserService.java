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
import org.springframework.stereotype.Service;

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
    public UserResponse updateUser(UUID userId, UserRequest request) {
        logger.debug("Updating user: {}", userId);

        Optional<User> optionalUser = userRepository.findById(userId);
        if (!optionalUser.isPresent()) {
            logger.error("User not found: {}", userId);
            throw new IllegalArgumentException("User not found");
        }

        User updatedUser = optionalUser.get();

        // Update email if provided
        if (request.email() != null && !request.email().isEmpty()) {
            logger.debug("Setting email for user {}: {}", userId, request.email());
            updatedUser.setEmail(request.email());
        }

        // Update full name if provided
        if (request.fullName() != null && !request.fullName().isEmpty()) {
            logger.debug("Setting full name for user {}: {}", userId, request.fullName());
            updatedUser.setFullName(request.fullName());
        }

        // Update role if provided
        if (request.role() != null) {
            logger.debug("Setting role for user {}: {}", userId, request.role());
            updatedUser.setRole(request.role());
        }

        // Update active status if provided
        if (request.active() != null) {
            logger.debug("Setting active status for user {}: {}", userId, request.active());
            updatedUser.setActive(request.active());
        }

        updatedUser = userRepository.save(updatedUser);

        // Update preferences if provided
        if (request.preferences() != null) {
            logger.debug("Updating preferences for user {}: {}", userId, request.preferences());
            for (String key : request.preferences().keySet()) {
                Preference pref = getPreference(updatedUser.getId(), key);

                switch (key) {
                    case "theme":
                        String themeValue = request.preferences().get("theme").toString();
                        logger.debug("Setting theme preference: {}", themeValue);
                        Theme theme = Theme.valueOf(themeValue.toUpperCase());
                        pref.setTheme(theme);
                        break;

                    case "currency":
                        String currencyValue = request.preferences().get("currency").toString();
                        logger.debug("Setting currency preference: {}", currencyValue);
                        Currency currency = Currency.valueOf(currencyValue.toUpperCase());
                        pref.setCurrency(currency);
                        break;

                    case "notifications":
                        Boolean notificationValue = (Boolean) request.preferences().get("notifications");
                        NotificationMode mode = notificationValue ? NotificationMode.ALL : NotificationMode.NONE;
                        logger.debug("Setting notification mode: {}", mode);
                        pref.setNotificationMode(mode);
                        break;
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
    public UserResponse getCurrentUser() {
        logger.debug("Getting current user");
        String email = "user@example.com"; // Placeholder - actual implementation would use SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get preferences
        Preference currencyPref = preferenceRepository.findByUserIdAndKey(user.getId(), "currency")
                .orElse(new Preference());
        currencyPref.setCurrency(Currency.INR);

        Preference themePref = preferenceRepository.findByUserIdAndKey(user.getId(), "theme")
                .orElse(new Preference());
        themePref.setTheme(Theme.DARK);

        Preference notificationPref = preferenceRepository.findByUserIdAndKey(user.getId(), "notifications")
                .orElse(new Preference());
        notificationPref.setNotificationMode(NotificationMode.ALL);

        return new UserResponse(
            user.getEmail(),
            user.getFullName(),
            user.getRole()
        );
    }

    /**
     * Get preference by user ID and key.
     */
    private Preference getPreference(UUID userId, String key) {
        return preferenceRepository.findByUserIdAndKey(userId, key)
                .orElse(new Preference());
    }
}
