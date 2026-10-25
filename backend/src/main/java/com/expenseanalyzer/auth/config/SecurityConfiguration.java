package com.expenseanalyzer.auth.config;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
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
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

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
    private final RateLimiter loginRateLimiter;
    private final RateLimiter registrationRateLimiter;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfiguration(UserRepository userRepository, RateLimiter loginRateLimiter, RateLimiter registrationRateLimiter, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.registrationRateLimiter = registrationRateLimiter;
        this.jwtTokenProvider = jwtTokenProvider;
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

    /**
     * Decodes Bearer tokens issued by JwtTokenProvider (HS256, shared secret).
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oauth.jwt.secret:default-secret-key-for-testing-only-32-bytes}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // Spring Security's default JwtAuthenticationConverter handles JWT claims properly without custom logic

    /**
     * Security Filter Chain bean.
     * Configures request authorization and session management for stateless JWT auth.
     */
    @Bean("securityFilterChainMain")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("Configuring SecurityFilterChain");
        return http
                // CSRF off for the API: auth is bearer-token only (Authorization header, no cookies/sessions),
                // so browsers never attach credentials automatically and there is nothing to forge.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})) // Validate Bearer tokens; principal = sub (email)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless for JWT
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // X-Frame-Options: SAMEORIGIN
                .build();
    }

    /**
     * Rate limiter bean for login attempts.
     * Uses Redis-backed rate limiting to prevent brute force attacks.
     * Configured to allow max 5 failed attempts per IP within 15 minutes.
     */
    @Bean
    public RateLimiter loginRateLimiter() {
        return new RateLimiter(); // Injected via @Value annotations
    }

    /**
     * Rate limiter bean for registration attempts.
     * Uses Redis-backed rate limiting to prevent spam.
     * Configured to allow max 3 failed attempts per IP within 15 minutes.
     */
    @Bean
    public RateLimiter registrationRateLimiter() {
        return new RateLimiter(); // Injected via @Value annotations
    }

}
