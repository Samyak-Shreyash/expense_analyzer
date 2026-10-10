package com.expenseanalyzer.user.service;

import com.expenseanalyzer.auth.dto.UpdateUserRequest;
import com.expenseanalyzer.auth.repository.UserRepository;
import com.expenseanalyzer.auth.dto.UserResponse;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.Theme;
import com.expenseanalyzer.user.model.Currency;
import com.expenseanalyzer.user.model.NotificationMode;
import com.expenseanalyzer.user.repository.PreferenceRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for user operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;

    public UserService(UserRepository userRepository, PreferenceRepository preferenceRepository) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Update user information.
     */
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User updatedUser = userRepository.findById(userId)
                .map(user -> {
                    if (request.email() != null && !request.email().isEmpty()) {
                        user.setEmail(request.email());
                    }
                    if (request.fullName() != null && !request.fullName().isEmpty()) {
                        user.setFullName(request.fullName());
                    }
                    if (request.role() != null) {
                        user.setRole(request.role());
                    }
                    if (request.active() != null) {
                        user.setActive(request.active());
                    }
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Update preferences if provided
        if (request.preferences() != null) {
            for (String key : request.preferences().keySet()) {
                Preference pref = getPreference(updatedUser.getId(), key);

                switch (key) {
                    case "theme":
                        String themeValue = request.preferences().get("theme").toString();
                        Theme theme = Theme.valueOf(themeValue.toUpperCase());
                        pref.setTheme(theme);
                        break;

                    case "currency":
                        String currencyValue = request.preferences().get("currency").toString();
                        Currency currency = Currency.valueOf(currencyValue.toUpperCase());
                        pref.setCurrency(currency);
                        break;

                    case "notifications":
                        Boolean notificationValue = (Boolean) request.preferences().get("notifications");
                        NotificationMode mode = notificationValue ? NotificationMode.ALL : NotificationMode.NONE;
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
     * Get preference by user ID and key.
     */
    private Preference getPreference(UUID userId, String key) {
        return preferenceRepository.findByUserIdAndKey(userId, key)
                .orElse(new Preference());
    }
}
