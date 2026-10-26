package com.expenseanalyzer.auth.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // JWT secret key must be at least 32 bytes for jjwt 0.12.6 (HS256)
    @Value("${app.oauth.jwt.secret}")
    private String SECRET_KEY;

    @Value("${app.oauth.jwt.tokenExpirationSeconds:3600}")
    private Long EXPIRATION_TIME;

    public String generateToken(Authentication authentication) {
        logger.debug("Generating JWT token");

        return Jwts.builder()
            .setSubject(authentication.getName())
            .setIssuer("expense-analyzer")
            .setIssuedAt(new java.util.Date(Instant.now().toEpochMilli()))
            .setExpiration(new java.util.Date(Instant.now().plusSeconds(EXPIRATION_TIME).toEpochMilli()))
            .claim("aud", "expense-analyzer-users")
            .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    }

    public Claims getClaims(String token) {
        try {
            // Get SecretKey from JWT library - returns typed object for verifyWith method
            var secretKey = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            logger.error("Failed to parse JWT token", e);
            throw e;
        }
    }
}
