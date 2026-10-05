package com.expenseanalyzer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.auth.dto.UserResponse;
import com.expenseanalyzer.auth.repository.UserRepository;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests for AuthService.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new org.springframework.security.crypto.password.PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "$2a$10$" + rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence password, String encodedPassword) {
            return true;
        }
    };
    private AuthService authService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "SecurePass123!";
    private static final String TEST_FULL_NAME = "Test User";
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        this.authService = new AuthService(userRepository, passwordEncoder);
    }

    private User createMockUser() {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$10$test");
        user.setFullName("Full Name");
        user.setRole(UserRole.USER); // Default role
        user.setActive(true);
        user.setCreatedAt(java.time.Instant.now());
        user.setUpdatedAt(java.time.Instant.now());
        user.setLastLoginAt(null);
        Map<String, Preference> preferences = new HashMap<>();
        preferences.put("currency", Preference.LIGHT);
        preferences.put("theme", Preference.DARK);
        preferences.put("notifications", Preference.LIGHT);
        user.setPreferences(preferences);
        return user;
    }

    /**
     * Test registration with valid data.
     */
    @Test
    void register_shouldCreateUser_whenDataIsValid() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        User savedUser = createMockUser();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User user = authService.register(request);

        // Assert
        assertThat(user.getId()).isEqualTo(TEST_USER_ID);
        assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(user.getPasswordHash()).startsWith("$2a$10$");
        assertThat(user.getFullName()).isEqualTo("Full Name");
        assertThat(user.getRole()).isEqualTo(UserRole.USER); // Default role
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getLastLoginAt()).isNull();
        assertThat(user.getPreferences()).hasSize(3);

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
        verify(userRepository, times(1)).save(any(User.class));
    }

    /**
     * Test registration with duplicate email.
     */
    @Test
    void register_shouldThrowException_whenEmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD);
        User existingUser = createMockUser();
        existingUser.setEmail(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email already registered");

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
    }

    /**
     * Test registration with missing email.
     */
    @Test
    void register_shouldThrowException_whenEmailMissing() {
        // Arrange
        RegisterRequest request = new RegisterRequest(null, TEST_PASSWORD);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email is required");
    }

    /**
     * Test registration with missing password.
     */
    @Test
    void register_shouldThrowException_whenPasswordMissing() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, null);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Password is required");
    }

    /**
     * Test registration with password less than 8 characters.
     */
    @Test
    void register_shouldThrowException_whenPasswordTooShort() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, "short");

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Password must be at least 8 characters");
    }

    /**
     * Test email registration check.
     */
    @Test
    void isEmailRegistered_shouldReturnTrue_whenEmailExists() {
        // Arrange
        User existingUser = createMockUser();
        existingUser.setEmail(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));

        // Act
        boolean result = authService.isEmailRegistered(TEST_EMAIL);

        // Assert
        assertThat(result).isTrue();

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
    }

    /**
     * Test email registration check for non-existent email.
     */
    @Test
    void isEmailRegistered_shouldReturnFalse_whenEmailDoesNotExist() {
        // Arrange
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

        // Act
        boolean result = authService.isEmailRegistered(TEST_EMAIL);

        // Assert
        assertThat(result).isFalse();

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
    }

    /**
     * Test UserResponse construction.
     */
    @Test
    void UserResponse_shouldConstructWithValidData() {
        // Arrange
        String email = "user@example.com";
        String fullName = "John Doe";

        // Act
        UserResponse response = new UserResponse(email, fullName, UserRole.USER);

        // Assert
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.fullName()).isEqualTo(fullName);
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    /**
     * Test PasswordEncoder encoding.
     */
    @Test
    void passwordEncoder_shouldEncodePassword() {
        // Arrange
        String password = "testpassword";

        // Act
        String encoded = passwordEncoder.encode(password);

        // Assert
        assertThat(encoded).startsWith("$2a$10$");
        assertThat(passwordEncoder.matches(password, encoded)).isTrue();
    }

    /**
     * Test User entity with new columns (preferences, last_login_at).
     */
    @Test
    void register_shouldInitializePreferencesWithDefaults() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        User savedUser = createMockUser();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User user = authService.register(request);

        // Assert
        Map<String, Preference> preferences = user.getPreferences();
        assertThat(preferences).containsEntry("currency", Preference.LIGHT);
        assertThat(preferences).containsEntry("theme", Preference.DARK);
        assertThat(preferences).containsEntry("notifications", Preference.LIGHT);

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
        verify(userRepository, times(1)).save(any(User.class));
    }

    /**
     * Test User entity with new columns (preferences, last_login_at).
     */
    @Test
    void register_shouldSetLastLoginAtToNull() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        User savedUser = createMockUser();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User user = authService.register(request);

        // Assert
        assertThat(user.getLastLoginAt()).isNull();

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
        verify(userRepository, times(1)).save(any(User.class));

        // Verify
        verify(userRepository).findByEmail(TEST_EMAIL);
        verify(userRepository, times(1)).save(any(User.class));
    }
}
