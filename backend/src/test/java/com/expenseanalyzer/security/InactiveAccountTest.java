package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.auth.repository.RefreshTokenRepository;
import com.expenseanalyzer.auth.service.RefreshTokenService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Inactive accounts (#349) are refused on login, refresh and access-token use. */
@SpringBootTest
class InactiveAccountTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PreferenceRepository preferenceRepository;

    private MockMvc mockMvc;
    private User active;
    private User inactive;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        active = user("active@example.com", true);
        inactive = user("inactive@example.com", false);
    }

    private User user(String email, boolean isActive) {
        User u = new User(email, passwordEncoder.encode(PASSWORD));
        u.setId(UUID.randomUUID());
        u.setRole(UserRole.USER);
        u.setActive(isActive);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private int login(User u, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int refresh(String raw) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", raw)))
                .andReturn().getResponse().getStatus();
    }

    private int me(User u) throws Exception {
        String token = jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(u.getEmail(), null));
        return mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void inactiveUserCannotLogInButActiveUserCan() throws Exception {
        assertThat(login(active, PASSWORD)).isEqualTo(200);
        assertThat(login(inactive, PASSWORD)).isEqualTo(401);
        assertThat(login(inactive, PASSWORD)).isEqualTo(login(inactive, "wrong-password"));
    }

    @Test
    void inactiveUserRefreshIsRejectedAndTokenRemovedButActiveUserRefreshes() throws Exception {
        assertThat(refresh(refreshTokenService.issue(active))).isEqualTo(200);

        assertThat(refresh(refreshTokenService.issue(inactive))).isEqualTo(401);
        // only the active user's rotated token is still usable; consumed rows stay to detect replays
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getConsumedAt() == null)).hasSize(1);
    }

    @Test
    void inactiveUserAccessTokenIs401ButActiveUserAccessTokenWorks() throws Exception {
        assertThat(me(active)).isEqualTo(200);
        assertThat(me(inactive)).isEqualTo(401);
    }

    @Test
    void accessTokenOfMissingUserKeepsPreviousBehaviour() throws Exception {
        User ghost = new User("ghost@example.com", "x");
        assertThat(me(ghost)).isNotEqualTo(401).isNotEqualTo(0);
    }
}
