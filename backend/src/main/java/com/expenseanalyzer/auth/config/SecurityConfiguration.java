package com.expenseanalyzer.auth.config;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Security configuration for Expense Analyzer.
 * Configures authentication, authorization, and security filters.
 */
@Configuration
@EnableMethodSecurity // enables @PreAuthorize on controllers/services
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
                .accountExpired(false).accountLocked(false).credentialsExpired(false).disabled(!user.isEnabled())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Decodes Bearer tokens issued by JwtTokenProvider (HS256, shared secret).
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oauth.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("app.oauth.jwt.secret must be at least 32 bytes");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secretBytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtTokenProvider.ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(JwtTokenProvider.AUDIENCE))));
        return decoder;
    }

    // Spring Security's default JwtAuthenticationConverter handles JWT claims properly without custom logic

    /**
     * Default JWT conversion plus an active check: a token whose user exists but is inactive gets 401.
     * A missing user is left alone (unchanged behaviour). One indexed lookup per authenticated request.
     */
    private Converter<Jwt, AbstractAuthenticationToken> activeUserJwtConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        return jwt -> {
            userRepository.findByEmail(jwt.getSubject()).ifPresent(user -> {
                if (!user.isEnabled()) {
                    throw new DisabledException("Account is inactive");
                }
                // iat has whole seconds: a token from the new login in the same second must still pass
                Instant changed = user.getPasswordChangedAt();
                if (changed != null && jwt.getIssuedAt().isBefore(changed.truncatedTo(ChronoUnit.SECONDS))) {
                    throw new CredentialsExpiredException("Password changed");
                }
            });
            return delegate.convert(jwt);
        };
    }

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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(activeUserJwtConverter()))) // Validate Bearer tokens; principal = sub (email)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless for JWT
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // X-Frame-Options: SAMEORIGIN
                .build();
    }
}
