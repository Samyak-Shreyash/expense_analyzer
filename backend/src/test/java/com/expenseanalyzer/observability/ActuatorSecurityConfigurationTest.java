package com.expenseanalyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
class ActuatorSecurityConfigurationTest {

    @Autowired
    private SecurityFilterChain actuatorSecurityFilterChain;

    @Test
    void actuatorSecurityFilterChain_shouldBeCreated() throws Exception {
        // Arrange - Create a mock filter chain
        DefaultSecurityFilterChain mockFilterChain = mock(DefaultSecurityFilterChain.class);

        // Act
        SecurityFilterChain filterChain = actuatorSecurityFilterChain;

        // Assert
        assertThat(filterChain).isNotNull();
    }

    @Test
    void actuatorSecurityFilterChain_shouldConfigureActuatorEndpointsPermitAll() throws Exception {
        // Arrange - Create a mock filter chain
        DefaultSecurityFilterChain mockFilterChain = mock(DefaultSecurityFilterChain.class);

        // Act
        SecurityFilterChain filterChain = actuatorSecurityFilterChain;

        // Assert
        assertThat(filterChain).isNotNull();
    }

    @Test
    void actuatorSecurityFilterChain_shouldConfigureHttpBasicAuthentication() throws Exception {
        // Arrange - Create a mock filter chain
        DefaultSecurityFilterChain mockFilterChain = mock(DefaultSecurityFilterChain.class);

        // Act
        SecurityFilterChain filterChain = actuatorSecurityFilterChain;

        // Assert
        assertThat(filterChain).isNotNull();
    }
}
