package com.expenseanalyzer.auth.service;

import com.expenseanalyzer.auth.dto.LoginRequest;
import com.expenseanalyzer.auth.dto.LoginResponse;
import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.auth.dto.RegisterResponse;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserService.
 */
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clear existing users for clean tests
        userRepository.deleteAll();
    }

    @Test
    void registerUser_ShouldCreateNewUserWithHashedPassword() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When
        RegisterResponse response = userService.registerUser(request);

        // Then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFullName()).isEqualTo("Test User");

        // Verify user was saved with hashed password
        User savedUser = userRepository.findById(response.getId()).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getFullName()).isEqualTo("Test User");
        assertThat(passwordEncoder.matches("password123", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.isActive()).isTrue();
    }

    @Test
    void registerUser_ShouldThrowExceptionWhenUserAlreadyExists() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("password123");

        // Register user first time
        userService.registerUser(request);

        // When & Then - should throw exception on second registration
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void loginUser_ShouldReturnValidJwtToken() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setFullName("Test User");
        registerRequest.setPassword("password123");

        userService.registerUser(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        // When
        LoginResponse response = userService.loginUser(loginRequest);

        // Then
        assertThat(response.getJwtToken()).isNotNull();
        assertThat(response.getId()).isEqualTo(response.getJwtToken());
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void loginUser_ShouldThrowExceptionWhenInvalidCredentials() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setFullName("Test User");
        registerRequest.setPassword("password123");

        userService.registerUser(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("wrongpassword");

        // When & Then
        assertThatThrownBy(() -> userService.loginUser(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void loginUser_ShouldThrowExceptionWhenUserNotFound() {
        // Given - user doesn't exist
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@example.com");
        loginRequest.setPassword("password123");

        // When & Then
        assertThatThrownBy(() -> userService.loginUser(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void registerUser_ShouldValidatePasswordLength() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("short");

        // When & Then - should throw exception for password too short
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @Test
    void registerUser_ShouldValidateEmailFormat() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email-format");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then - should throw exception for invalid email
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void registerUser_ShouldValidateEmailNotBlank() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then - should throw exception for empty email
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email is required");
    }
}
