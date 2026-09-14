package com.expenseanalyzer.auth.config;

import com.expenseanalyzer.auth.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for JWT authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AuthSecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public AuthSecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF is not needed for stateless JWT authentication
                .csrf(csrf -> csrf.disable())
                // Configure request authorization rules
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints - no authentication required
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        // Actuator endpoints for platform monitoring
                        .requestMatchers("/actuator/**").permitAll()
                        // Health and metrics endpoints
                        .requestMatchers("/health/**", "/metrics", "/prometheus/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Configure session management for stateless JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Apply JWT authentication filter
                .addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.FilterChainProxy.class)
                .build();
    }
}
