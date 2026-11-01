package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.repository.AuditLogRepository;
import com.expenseanalyzer.auth.config.RateLimiter;
import com.expenseanalyzer.auth.dto.ChangePasswordRequest;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ChangePasswordTest {

    private static final String EMAIL = "pw-change@example.com";
    private static final String OLD = "old-password-1";
    private static final String NEW = "new-password-2";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private RateLimiter rateLimiter;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = new User(EMAIL, passwordEncoder.encode(OLD));
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from refresh_tokens where user_id = ?", user.getId());
        jdbc.update("delete from audit_logs where user_id = ?", user.getId());
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
    }

    private ResultActions change(String current, String next) throws Exception {
        return mockMvc.perform(put("/api/user/me/password").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void successChangesPasswordRevokesRefreshTokensAndAudits() throws Exception {
        String body = login(OLD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldRefresh = JsonPath.read(body, "$.refreshToken");

        change(OLD, NEW).andExpect(status().isNoContent());

        login(NEW).andExpect(status().isOk());
        login(OLD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(auditLogRepository.findAll()).anyMatch(a -> a.getAction() == AuditAction.AUTH_PASSWORD_CHANGED
                && user.getId().equals(a.getUserId()));
        verify(rateLimiter).reset("pwd", user.getId());
    }

    @Test
    void wrongCurrentPasswordIsBadRequestAndNothingChanges() throws Exception {
        change("not-the-password", NEW).andExpect(status().isBadRequest());
        login(OLD).andExpect(status().isOk());
        login(NEW).andExpect(status().isUnauthorized());
    }

    @Test
    void weakOrMissingNewPasswordIsBadRequest() throws Exception {
        change(OLD, "short").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/user/me/password").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + OLD + "\"}"))
                .andExpect(status().isBadRequest());
        login(OLD).andExpect(status().isOk());
    }

    @Test
    void loginAboveTheLimitAnswers429AndSuccessResets() throws Exception {
        login(OLD).andExpect(status().isOk());
        verify(rateLimiter).resetLogin(eq(EMAIL), anyString());

        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiter).checkLogin(eq(EMAIL), anyString());
        login(OLD).andExpect(status().isTooManyRequests());
    }

    @Test
    void newPasswordOverTheBcryptLimitIsBadRequest() throws Exception {
        change(OLD, "x".repeat(73)).andExpect(status().isBadRequest());
    }

    @Test
    void everyAttemptIsCountedAndLimitAnswers429() throws Exception {
        change("wrong-password", NEW).andExpect(status().isBadRequest());
        verify(rateLimiter).check("pwd", user.getId());

        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(rateLimiter).check(any(), any());
        change(OLD, NEW).andExpect(status().isTooManyRequests());
        login(OLD).andExpect(status().isOk());
    }

    @Test
    void redisFailureAnswers503() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(rateLimiter).check(any(), any());
        change(OLD, NEW).andExpect(status().isServiceUnavailable());
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/user/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbb\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void toStringHidesPasswords() {
        String s = new ChangePasswordRequest(OLD, NEW).toString();
        assertThat(s).doesNotContain(OLD).doesNotContain(NEW);
    }
}
