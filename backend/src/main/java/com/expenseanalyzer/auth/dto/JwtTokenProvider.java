package com.expenseanalyzer.auth.dto;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private final JwtEncoder encoder;

    public JwtTokenProvider(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String generateToken(Authentication authentication) throws AuthenticationException {
        logger.debug("Generating JWT token for user: {}", authentication.getName());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("expense-analyzer")
                .subject("expense-analyzer")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(Collections.singletonList("expense-analyzer-users"))
                .build();

        try {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            logger.debug("JWT token generated successfully for user: {}", authentication.getName());
            return token;
        } catch (Exception e) {
            logger.error("Failed to generate JWT token for user: {}", authentication.getName(), e);
            throw e;
        }
    }
}
