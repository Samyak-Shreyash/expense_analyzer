package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserEndpointSecurityTest {

    private static final String EMAIL = "alice@example.com";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth.jwt.secret:default-secret-key-for-testing-only-32-bytes}")
    private String jwtSecret;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PreferenceRepository preferenceRepository;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail(EMAIL);
        alice.setFullName("Alice");
        alice.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alice));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    @Test
    void meReturnsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void meReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer("ghost@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void meRejectsMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String signedToken(String secret, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(EMAIL)
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .build();
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void meAcceptsHandBuiltValidToken() throws Exception {
        // Control for the two tests below: the helper's tokens are valid when secret and expiry are.
        mockMvc.perform(get("/api/user/me").header("Authorization", signedToken(jwtSecret, Instant.now().plusSeconds(600))))
                .andExpect(status().isOk());
    }

    @Test
    void meRejectsExpiredToken() throws Exception {
        // Well past the decoder's 60s clock skew.
        mockMvc.perform(get("/api/user/me").header("Authorization", signedToken(jwtSecret, Instant.now().minusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsTokenWithWrongSignature() throws Exception {
        mockMvc.perform(get("/api/user/me").header("Authorization",
                        signedToken("a-completely-different-secret-key-of-32-plus-bytes", Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersPathIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/users/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnUserSucceeds() throws Exception {
        mockMvc.perform(put("/api/user/" + alice.getId()).header("Authorization", bearer(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Alice B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice B"));
    }

    @Test
    void authenticatedPostWithoutCsrfTokenIsNotRejected() throws Exception {
        // No POST handler on /api/user/me: 405 proves the request got past the CSRF filter (which would 403).
        mockMvc.perform(post("/api/user/me").header("Authorization", bearer(EMAIL)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateOtherUserIsForbidden() throws Exception {
        User bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setEmail("bob@example.com");
        when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

        mockMvc.perform(put("/api/user/" + bob.getId()).header("Authorization", bearer(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void selfRoleEscalationIsForbidden() throws Exception {
        mockMvc.perform(put("/api/user/" + alice.getId()).header("Authorization", bearer(EMAIL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
}
