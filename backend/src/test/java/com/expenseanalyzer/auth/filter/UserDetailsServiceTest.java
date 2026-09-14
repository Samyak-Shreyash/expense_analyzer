package com.expenseanalyzer.auth.filter;

import com.expenseanalyzer.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserDetailsService.
 */
@SpringBootTest
class UserDetailsServiceTest {

    @Autowired
    private UserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = new User();
        testUser.setId(java.util.UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashed-password");
        testUser.setActive(true);
        testUser.setFullName("Test User");
    }

    @Test
    void loadUserByUsername_ShouldReturnValidUserDetails() {
        // Given
        String email = "test@example.com";

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(email);
        assertThat(userDetails.getPassword()).isEqualTo(testUser.getPasswordHash());
        assertThat(userDetails.getAuthorities()).isEmpty();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_ShouldReturnUserWithRoleFromFullName() {
        // Given
        String email = "test@example.com";

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Then - Role should be derived from full name
        assertThat(userDetails.getAuthorities()).isNotEmpty();
        assertThat(userDetails.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("TEST USER");
    }

    @Test
    void loadUserByUsername_ShouldReturnDisabledUserWhenInactive() {
        // Given - user is inactive
        testUser.setActive(false);

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_ShouldThrowExceptionWhenUserNotFound() {
        // Given - user doesn't exist
        String email = "nonexistent@example.com";

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
                .hasMessageContaining("User not found: nonexistent@example.com");
    }

    @Test
    void loadUserByUsername_ShouldReturnValidDetailsForActiveUser() {
        // Given - user is active
        testUser.setActive(true);

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_ShouldReturnValidDetailsForInactiveUser() {
        // Given - user is inactive
        testUser.setActive(false);

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_ShouldReturnValidDetailsForUserWithNullFullName() {
        // Given - user has null full name
        testUser.setFullName(null);

        // When
        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
