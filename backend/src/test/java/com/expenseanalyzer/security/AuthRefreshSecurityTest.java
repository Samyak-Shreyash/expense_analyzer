package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.repository.RefreshTokenRepository;
import com.expenseanalyzer.auth.service.RefreshTokenService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthRefreshSecurityTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PreferenceRepository preferenceRepository;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = new User(EMAIL, passwordEncoder.encode(PASSWORD));
        alice.setId(UUID.randomUUID());
        alice.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alice));
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
    }

    private MvcResult refresh(String raw) throws Exception {
        var request = post("/api/auth/refresh");
        if (raw != null) {
            request.cookie(new Cookie("refresh_token", raw));
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void loginSetsHttpOnlyStrictRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.token")).isNotBlank();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refresh_token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
    }

    @Test
    void refreshWithValidCookieReturnsWorkingAccessToken() throws Exception {
        MvcResult result = refresh(refreshTokenService.issue(alice));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() throws Exception {
        String raw = refreshTokenService.issue(alice);

        MvcResult first = refresh(raw);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(first.getResponse().getHeader("Set-Cookie"))
                .startsWith("refresh_token=")
                .doesNotStartWith("refresh_token=" + raw);

        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        assertThat(refresh(null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithUnknownTokenIs401() throws Exception {
        assertThat(refresh("not-a-real-token").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithExpiredTokenIs401() throws Exception {
        String raw = new RefreshTokenService(refreshTokenRepository, userRepository, -60).issue(alice);
        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String raw = refreshTokenService.issue(alice);

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
    }
}
