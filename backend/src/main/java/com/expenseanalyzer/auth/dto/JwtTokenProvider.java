package com.expenseanalyzer.auth.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // JWT secret key must be at least 32 bytes for jjwt 0.12.6 (HS256)
    @Value("${app.oauth.jwt.secret:default-secret-key-for-testing-only-32-bytes}")
    private final String SECRET_KEY;

    @Value("${app.oauth.jwt.tokenExpirationSeconds:3600}")
    private final Long EXPIRATION_TIME;
        

    public String generateToken(Authentication authentication) {
        logger.debug("Generating JWT token for user: {}", authentication.getName());

        // Create JWT token using jjwt library
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME); // 1 hour

        return Jwts.builder()
            .setSubject(authentication.getName())
            .setIssuer("expense-analyzer")
            .setIssuedAt(now)
            .setExpiration(expiration)
            .claim("aud", "expense-analyzer-users")
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
            .compact();
    }

    public Claims getClaims(String token) {
        try {
            return Jwts.parser()
                .setSigningKey(SECRET_KEY.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (Exception e) {
            logger.error("Failed to parse JWT token", e);
            throw e;
        }
    }
}
