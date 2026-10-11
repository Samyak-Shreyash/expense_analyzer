package com.expenseanalyzer.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Security configuration for Expense Analyzer.
 * Configures authentication, authorization, and security filters.
 */
@Configuration
// @EnableWebSecurity removed - Spring Boot 4.1.1 no longer requires it
// AuthenticationManager is created automatically when using ProviderManager
public class SecurityConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);
    private final UserRepository userRepository;

    public SecurityConfiguration(UserRepository userRepository) {
        this.userRepository = userRepository;
        logger.debug("SecurityConfiguration initialized");
    }

    /**
     * Authentication Provider bean.
     * Configures the provider to use DaoAuthentication with UserRepository.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        logger.debug("Creating AuthenticationProvider bean");
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        logger.debug("Creating AuthenticationManager bean");
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.oauth.jwt.secret}") String secret) {
        logger.debug("Creating JwtEncoder with secret length: {}", secret.length());
        return new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }

    /**
     * Password Encoder bean.
     * Uses BCrypt for secure password hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        logger.debug("Creating PasswordEncoder bean");
        return new BCryptPasswordEncoder();
    }

    /**
     * UserRepository bean.
     */
    // Simplified UserDetailsService using Spring Security's built-in method
    @Bean
    public UserDetailsService userDetailsService() {
        logger.debug("Creating UserDetailsService bean");
        return username -> userRepository.findByEmail(username)
            .map(user -> org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .accountExpired(false).accountLocked(false).credentialsExpired(false).disabled(false)
                .build())
            .orElse(null);
    }

    // Spring Security's default JwtAuthenticationConverter handles JWT claims properly without custom logic

    /**
     * Security Filter Chain bean.
     * Configures CSRF, request authorization, and session management.
     */
    @Bean("securityFilterChainMain")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("Configuring SecurityFilterChain");
        return http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless JWT auth
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/auth/**", "/api/users/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Stateless for JWT
                )
                .build();
    }

}
