package com.expenseanalyzer.user.service;

import com.expenseanalyzer.auth.dto.UpdateUserRequest;
import com.expenseanalyzer.auth.repository.UserRepository;
import com.expenseanalyzer.auth.dto.UserResponse;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for user operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        return new UserResponse(
            updatedUser.getEmail(),
            updatedUser.getFullName(),
            updatedUser.getRole()
        );
    }
}
