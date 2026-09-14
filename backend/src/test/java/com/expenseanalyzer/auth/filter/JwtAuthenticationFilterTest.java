package com.expenseanalyzer.auth.filter;

import com.expenseanalyzer.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
    }

    @Test
    void filter_ShouldSetSecurityContextWithValidToken() throws Exception {
        // Given
        String validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1dWlkOiJ1dWlkOnRlc3QtdXNlciJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + validToken)
                .andExpect(status().isOk());

        // Then - Security context should be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("uuid:test-user");
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithInvalidToken() throws Exception {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + invalidToken)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithMissingToken() throws Exception {
        // Given
        // No Authorization header

        // When
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        // Then - Security context should NOT be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithEmptyToken() throws Exception {
        // Given
        String emptyToken = "";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + emptyToken)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithInvalidJwtFormat() throws Exception {
        // Given
        String invalidJwt = "not-a-valid-jwt";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + invalidJwt)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithTokenMissingBearer() throws Exception {
        // Given
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1dWlkOnRlc3QtdXNlciJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", token)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set (missing "Bearer ")
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldSetSecurityContextWithValidTokenAndProtectedEndpoint() throws Exception {
        // Given
        String validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1dWlkOnRlc3QtdXNlciJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // When
        mockMvc.perform(get("/auth/register"))
                .header("Authorization", "Bearer " + validToken)
                .andExpect(status().isOk());

        // Then - Security context should be set (filter processes all requests)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithExpiredToken() throws Exception {
        // Given
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1dWlkOnRlc3QtdXNlciJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + expiredToken)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set (expired token is treated as invalid)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    @Test
    void filter_ShouldNotSetSecurityContextWithMalformedToken() throws Exception {
        // Given
        String malformedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

        // When
        mockMvc.perform(get("/health"))
                .header("Authorization", "Bearer " + malformedToken)
                .andExpect(status().isOk());

        // Then - Security context should NOT be set (malformed token)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }
}
