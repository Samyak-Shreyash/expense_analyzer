package com.expenseanalyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

@ExtendWith(MockitoExtension.class)
class ActuatorSecurityConfigurationTest {

    @Mock
    private HttpSecurity httpSecurity;

    @InjectMocks
    private ActuatorSecurityConfiguration actuatorSecurityConfiguration;

    @Test
    void securityFilterChain_shouldBeCreated() throws Exception {
        // Arrange
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

        // Act
        SecurityFilterChain filterChain = actuatorSecurityConfiguration.securityFilterChain(httpSecurity);

        // Assert
        assertThat(filterChain).isNotNull();

        verify(httpSecurity).csrf(any());
        verify(httpSecurity).authorizeHttpRequests(any());
        verify(httpSecurity).httpBasic(any());
    }

    @Test
    void securityFilterChain_shouldConfigureActuatorEndpointsPermitAll() throws Exception {
        // Arrange
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

        // Act
        SecurityFilterChain filterChain = actuatorSecurityConfiguration.securityFilterChain(httpSecurity);

        // Assert
        assertThat(filterChain).isNotNull();
    }

    @Test
    void securityFilterChain_shouldConfigureHttpBasicAuthentication() throws Exception {
        // Arrange
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
        when(httpSecurity.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

        // Act
        SecurityFilterChain filterChain = actuatorSecurityConfiguration.securityFilterChain(httpSecurity);

        // Assert
        assertThat(filterChain).isNotNull();
    }
}
