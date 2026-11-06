package com.expenseanalyzer.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expenseanalyzer.auth.config.SecurityConfiguration;
import com.expenseanalyzer.user.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtValidationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-dummy";

    private final SecurityConfiguration config = new SecurityConfiguration(Mockito.mock(UserRepository.class));

    private static String token(String issuer, String audience) {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("a@example.com")
                .issuer(issuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("aud", audience)
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void acceptsOurIssuerAndAudience() {
        String token = token("expense-analyzer", "expense-analyzer-users");
        assertThatCode(() -> config.jwtDecoder(SECRET).decode(token)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongIssuer() {
        String token = token("someone-else", "expense-analyzer-users");
        assertThatThrownBy(() -> config.jwtDecoder(SECRET).decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsWrongAudience() {
        String token = token("expense-analyzer", "other");
        assertThatThrownBy(() -> config.jwtDecoder(SECRET).decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsShortSecretAtStartup() {
        assertThatThrownBy(() -> config.jwtDecoder("too-short")).isInstanceOf(IllegalArgumentException.class);
    }
}
