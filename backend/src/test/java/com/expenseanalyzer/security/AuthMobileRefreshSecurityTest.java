package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.dto.RefreshRequest;
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
class AuthMobileRefreshSecurityTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "password123";
    private static final String CLIENT_TYPE = "X-Client-Type";

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

    private static String body(String raw) {
        return "{\"refreshToken\":" + (raw == null ? "null" : "\"" + raw + "\"") + "}";
    }

    private static String loginBody() {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private MvcResult mobileRefresh(String raw) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .header(CLIENT_TYPE, "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(raw)))
                .andReturn();
    }

    private MvcResult cookieRefresh(String raw) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", raw))).andReturn();
    }

    private static String json(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    @Test
    void mobileLoginReturnsBothTokensAndNoCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header(CLIENT_TYPE, "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result, "$.token")).isNotBlank();
        assertThat(json(result, "$.refreshToken")).isNotBlank();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void headerValueIsCaseInsensitive() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header(CLIENT_TYPE, "MoBiLe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andReturn();
        assertThat(json(result, "$.refreshToken")).isNotBlank();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void webLoginResponseHasNoRefreshTokenInBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("refresh_token=");
    }

    @Test
    void mobileRefreshReturnsWorkingAccessTokenAndNewRefreshToken() throws Exception {
        String raw = refreshTokenService.issue(alice);
        MvcResult result = mobileRefresh(raw);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(json(result, "$.refreshToken")).isNotBlank().isNotEqualTo(raw);

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + json(result, "$.token")))
                .andExpect(status().isOk());
    }

    @Test
    void mobileReuseOfRotatedTokenIs401() throws Exception {
        String raw = refreshTokenService.issue(alice);
        assertThat(mobileRefresh(raw).getResponse().getStatus()).isEqualTo(200);
        MvcResult reuse = mobileRefresh(raw);
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(reuse.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void mobileMissingBlankNullUnknownAndExpiredTokensAre401WithoutCookie() throws Exception {
        MvcResult noBody = mockMvc.perform(post("/api/auth/refresh").header(CLIENT_TYPE, "mobile")).andReturn();
        String expired = new RefreshTokenService(refreshTokenRepository, userRepository, -60).issue(alice);

        MvcResult[] results = {
                noBody, mobileRefresh(null), mobileRefresh(""), mobileRefresh("   "),
                mobileRefresh("not-a-real-token"), mobileRefresh(expired) };
        for (MvcResult r : results) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
        }
    }

    @Test
    void mobileLogoutRevokesAndLaterRefreshIs401() throws Exception {
        String raw = refreshTokenService.issue(alice);
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .header(CLIENT_TYPE, "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(raw)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(mobileRefresh(raw).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void mobileLogoutWithoutBodyIs204WithoutCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout").header(CLIENT_TYPE, "mobile"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void mobileRefreshIgnoresCookieAndTokenSurvives() throws Exception {
        String raw = refreshTokenService.issue(alice);
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .header(CLIENT_TYPE, "mobile")
                        .cookie(new Cookie("refresh_token", raw)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();

        assertThat(cookieRefresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void mobileLogoutIgnoresCookie() throws Exception {
        String raw = refreshTokenService.issue(alice);
        mockMvc.perform(post("/api/auth/logout").header(CLIENT_TYPE, "mobile")
                        .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isNoContent());
        assertThat(cookieRefresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void webRefreshIgnoresBodyTokenAndTokenSurvives() throws Exception {
        String raw = refreshTokenService.issue(alice);
        MvcResult noHeader = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andReturn();
        assertThat(noHeader.getResponse().getStatus()).isEqualTo(401);

        MvcResult otherValue = mockMvc.perform(post("/api/auth/refresh")
                        .header(CLIENT_TYPE, "android")
                        .contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andReturn();
        assertThat(otherValue.getResponse().getStatus()).isEqualTo(401);

        assertThat(mobileRefresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void webLogoutIgnoresBodyTokenAndTokenSurvives() throws Exception {
        String raw = refreshTokenService.issue(alice);
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isNoContent());
        assertThat(mobileRefresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void tokenIssuedOverOneTransportWorksOverTheOtherOnce() throws Exception {
        String viaCookie = refreshTokenService.issue(alice);
        MvcResult toMobile = mobileRefresh(viaCookie);
        assertThat(toMobile.getResponse().getStatus()).isEqualTo(200);
        String mobileToken = json(toMobile, "$.refreshToken");

        assertThat(cookieRefresh(mobileToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(cookieRefresh(mobileToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshRequestToStringHidesToken() {
        assertThat(new RefreshRequest("secret-value").toString()).doesNotContain("secret-value");
    }
}
