package com.expenseanalyzer.user.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for User entity.
 */
class UserTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD_HASH = "$2a$10$hashedpassword";
    private static final String TEST_FULL_NAME = "Test User";
    private static final Map<String, Object> TEST_PREFERENCES = new HashMap<>();
    private static final String TEST_CURRENCY = "INR";
    private static final String TEST_THEME = "dark";
    private static final Boolean TEST_NOTIFICATIONS = true;

    @Test
    void constructor_shouldCreateUser_withDefaults() {
        // Arrange
        User user = new User();

        // Act
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // Assert
        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getFullName()).isNull();
        assertThat(user.getPreferences()).isEmpty();
        assertThat(user.isActive()).isTrue();
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void constructor_shouldCreateUser_withAllFields() {
        // Arrange
        User user = new User();
        user.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash(TEST_PASSWORD_HASH);
        user.setFullName(TEST_FULL_NAME);
        user.setPreferences(TEST_PREFERENCES);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());

        // Assert
        assertThat(user.getId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(TEST_PASSWORD_HASH);
        assertThat(user.getFullName()).isEqualTo(TEST_FULL_NAME);
        assertThat(user.getPreferences()).isEqualTo(TEST_PREFERENCES);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void setEmail_shouldUpdateEmail() {
        // Arrange
        User user = new User();
        user.setEmail(null);

        // Act
        user.setEmail(TEST_EMAIL);

        // Assert
        assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    void setPasswordHash_shouldUpdatePasswordHash() {
        // Arrange
        User user = new User();
        user.setPasswordHash(null);

        // Act
        user.setPasswordHash(TEST_PASSWORD_HASH);

        // Assert
        assertThat(user.getPasswordHash()).isEqualTo(TEST_PASSWORD_HASH);
    }

    @Test
    void setFullName_shouldUpdateFullName() {
        // Arrange
        User user = new User();
        user.setFullName(null);

        // Act
        user.setFullName(TEST_FULL_NAME);

        // Assert
        assertThat(user.getFullName()).isEqualTo(TEST_FULL_NAME);
    }

    @Test
    void setPreferences_shouldUpdatePreferences() {
        // Arrange
        User user = new User();
        user.setPreferences(null);

        // Act
        user.setPreferences(TEST_PREFERENCES);

        // Assert
        assertThat(user.getPreferences()).isEqualTo(TEST_PREFERENCES);
        assertThat(user.getPreferences().get("currency")).isEqualTo(TEST_CURRENCY);
    }

    @Test
    void setActive_shouldUpdateActiveStatus() {
        // Arrange
        User user = new User();
        user.setActive(false);

        // Act
        user.setActive(true);

        // Assert
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void setLastLoginAt_shouldUpdateLastLoginTime() {
        // Arrange
        User user = new User();
        user.setLastLoginAt(null);
        Instant pastTime = Instant.parse("2026-01-01T00:00:00Z");

        // Act
        user.setLastLoginAt(pastTime);

        // Assert
        assertThat(user.getLastLoginAt()).isEqualTo(pastTime);
    }

    @Test
    void recordLogin_shouldUpdateLastLoginTime() {
        // Arrange
        User user = new User();
        user.setLastLoginAt(null);

        // Act
        user.recordLogin();

        // Assert
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void isEnabled_shouldReturnActiveStatus_whenActive() {
        // Arrange
        User user = new User();
        user.setActive(true);

        // Act
        boolean result = user.isEnabled();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isEnabled_shouldReturnFalse_whenInactive() {
        // Arrange
        User user = new User();
        user.setActive(false);

        // Act
        boolean result = user.isEnabled();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void equals_shouldReturnTrue_whenSameUser() {
        // Arrange
        User user1 = new User();
        User user2 = new User();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        user1.setId(testId);
        user2.setId(testId);

        boolean result = user1.equals(user2);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void equals_shouldReturnFalse_whenDifferentUsers() {
        // Arrange
        User user1 = new User();
        User user2 = new User();
        UUID testId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID testId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        // Act
        user1.setId(testId1);
        user2.setId(testId2);

        boolean result = user1.equals(user2);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void equals_shouldReturnTrue_whenSameObject() {
        // Arrange
        User user = new User();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        user.setId(testId);
        boolean result = user.equals(user);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void equals_shouldReturnFalse_whenNull() {
        // Arrange
        User user = new User();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        user.setId(testId);
        boolean result = user.equals(null);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void equals_shouldReturnFalse_whenNotInstanceOfUser() {
        // Arrange
        User user = new User();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        user.setId(testId);
        boolean result = user.equals(new Object());

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void hashCode_shouldReturnSameValue_whenSameUser() {
        // Arrange
        User user1 = new User();
        User user2 = new User();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        user1.setId(testId);
        user2.setId(testId);
        int hash1 = user1.hashCode();
        int hash2 = user2.hashCode();

        // Assert
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashCode_shouldReturnZero_whenNoId() {
        // Arrange
        User user = new User();

        // Act
        int result = user.hashCode();

        // Assert
        assertThat(result).isEqualTo(0);
    }

    @Test
    void constructor_shouldThrow_whenEmailIsNull() {
        // Arrange
        User user = new User();
        user.setEmail(null);

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenPasswordHashIsNull() {
        // Arrange
        User user = new User();
        user.setPasswordHash(null);

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenFullNameIsTooLong() {
        // Arrange
        User user = new User();
        String longName = "A".repeat(256);

        // Act & Assert
        assertThatThrownBy(() -> user.setFullName(longName))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenEmailIsTooLong() {
        // Arrange
        User user = new User();
        String longEmail = "a".repeat(256) + "@example.com";

        // Act & Assert
        assertThatThrownBy(() -> user.setEmail(longEmail))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenEmailIsNotValidFormat() {
        // Arrange
        User user = new User();
        String invalidEmail = "invalid-email";

        // Act & Assert
        assertThatThrownBy(() -> user.setEmail(invalidEmail))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenFullNameIsNull() {
        // Arrange
        User user = new User();
        user.setFullName(null);

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenEmailIsBlank() {
        // Arrange
        User user = new User();
        user.setEmail("");

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenPasswordHashIsBlank() {
        // Arrange
        User user = new User();
        user.setPasswordHash("");

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void getPreferences_shouldReturnMap_whenSet() {
        // Arrange
        User user = new User();
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("currency", "INR");
        preferences.put("theme", "dark");
        preferences.put("notifications", true);
        user.setPreferences(preferences);

        // Act
        Map<String, Object> result = user.getPreferences();

        // Assert
        assertThat(result).isEqualTo(preferences);
        assertThat(result.get("currency")).isEqualTo("INR");
        assertThat(result.get("theme")).isEqualTo("dark");
    }

    @Test
    void getPreferences_shouldReturnEmptyMap_whenNotSet() {
        // Arrange
        User user = new User();

        // Act
        Map<String, Object> result = user.getPreferences();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void setPreferences_shouldAcceptNull() {
        // Arrange
        User user = new User();

        // Act
        user.setPreferences(null);

        // Assert
        assertThat(user.getPreferences()).isNull();
    }

    @Test
    void recordLogin_shouldUpdateLastLoginAt_withCurrentTime() {
        // Arrange
        User user = new User();
        Instant pastTime = Instant.parse("2026-01-01T00:00:00Z");
        user.setLastLoginAt(pastTime);

        // Act
        user.recordLogin();

        // Assert
        assertThat(user.getLastLoginAt()).isAfter(pastTime);
    }

    @Test
    void isEnabled_shouldReturnTrue_whenActiveIsTrue() {
        // Arrange
        User user = new User();
        user.setActive(true);

        // Act
        boolean result = user.isEnabled();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isEnabled_shouldReturnFalse_whenActiveIsFalse() {
        // Arrange
        User user = new User();
        user.setActive(false);

        // Act
        boolean result = user.isEnabled();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void constructor_shouldThrow_whenEmailContainsSpecialChars() {
        // Arrange
        User user = new User();
        String specialEmail = "test@example.com";

        // Act & Assert
        assertThatThrownBy(() -> user.setEmail(specialEmail))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenPasswordHashIsNotValidFormat() {
        // Arrange
        User user = new User();
        String invalidHash = "not-a-hash";

        // Act & Assert
        assertThatThrownBy(() -> user.setPasswordHash(invalidHash))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenFullNameIsBlank() {
        // Arrange
        User user = new User();
        user.setFullName("");

        // Act & Assert
        assertThatThrownBy(() -> user)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenEmailContainsWhitespace() {
        // Arrange
        User user = new User();
        String whitespaceEmail = "test@ example.com";

        // Act & Assert
        assertThatThrownBy(() -> user.setEmail(whitespaceEmail))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenPasswordHashIsTooLong() {
        // Arrange
        User user = new User();
        String longHash = "a".repeat(256);

        // Act & Assert
        assertThatThrownBy(() -> user.setPasswordHash(longHash))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenFullNameIsTooLong() {
        // Arrange
        User user = new User();
        String longName = "A".repeat(256);

        // Act & Assert
        assertThatThrownBy(() -> user.setFullName(longName))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenEmailIsTooLong() {
        // Arrange
        User user = new User();
        String longEmail = "a".repeat(256) + "@example.com";

        // Act & Assert
        assertThatThrownBy(() -> user.setEmail(longEmail))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenPasswordHashIsTooLong() {
        // Arrange
        User user = new User();
        String longHash = "a".repeat(256);

        // Act & Assert
        assertThatThrownBy(() -> user.setPasswordHash(longHash))
            .isInstanceOf(ConstraintViolationException.class);
    }
}
