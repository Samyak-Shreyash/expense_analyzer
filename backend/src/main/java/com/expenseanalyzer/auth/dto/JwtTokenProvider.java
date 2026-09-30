package com.expenseanalyzer.auth.dto;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;

@Component
public class JwtTokenProvider {
    private final JwtEncoder encoder;

    public JwtTokenProvider(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String generateToken(Authentication authentication) throws AuthenticationException {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("expense-analyzer")
                .subject("expense-analyzer")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(Collections.singletonList("expense-analyzer-users"))
                .build();

        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
