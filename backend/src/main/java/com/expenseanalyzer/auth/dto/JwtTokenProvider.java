package com.expenseanalyzer.auth.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // HS256 needs a secret of at least 32 bytes
    @Value("${app.oauth.jwt.secret}")
    private String SECRET_KEY;

    @Value("${app.oauth.jwt.tokenExpirationSeconds:3600}")
    private Long EXPIRATION_TIME;

    public String generateToken(Authentication authentication) {
        logger.debug("Generating JWT token");

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(authentication.getName())
            .issuer("expense-analyzer")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(EXPIRATION_TIME))
            .claim("aud", "expense-analyzer-users")
            .build();
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
}
