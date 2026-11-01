package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserMeTest {

    private static final String ALICE = "alice@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail(ALICE);
        alice.setFullName("Alice");
        alice.setRole(UserRole.USER);
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(userRepository.findByEmail(ALICE)).thenReturn(Optional.of(alice));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from preferences");
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
    }

    private ResultActions putMe(String body) throws Exception {
        return mockMvc.perform(put("/api/user/me").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getMe() throws Exception {
        return mockMvc.perform(get("/api/user/me").header("Authorization", token()));
    }

    @Test
    void updateOwnDetailsAndReadBack() throws Exception {
        putMe("{\"fullName\":\"Alice B\",\"preferences\":{\"theme\":\"dark\",\"currency\":\"inr\",\"notifications\":false}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ALICE))
                .andExpect(jsonPath("$.fullName").value("Alice B"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.currency").value("inr"))
                .andExpect(jsonPath("$.preferences.notifications").value(false));
        getMe().andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice B"))
                .andExpect(jsonPath("$.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.currency").value("inr"))
                .andExpect(jsonPath("$.preferences.notifications").value(false));
    }

    @Test
    void secondUpdateOverwritesPreference() throws Exception {
        putMe("{\"preferences\":{\"theme\":\"dark\"}}").andExpect(status().isOk());
        putMe("{\"preferences\":{\"theme\":\"light\"}}").andExpect(status().isOk());
        getMe().andExpect(jsonPath("$.preferences.theme").value("light"));
        assertThat(jdbc.queryForList("select id from preferences")).hasSize(1);
    }

    @Test
    void getWithNoPreferencesReturnsEmptyPreferences() throws Exception {
        getMe().andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ALICE))
                .andExpect(jsonPath("$.preferences").isMap());
    }

    @Test
    void invalidNamesAreBadRequest() throws Exception {
        putMe("{\"fullName\":\"   \"}").andExpect(status().isBadRequest());
        putMe("{\"fullName\":\"" + "a".repeat(256) + "\"}").andExpect(status().isBadRequest());
        putMe("{\"fullName\":\"" + "a".repeat(255) + "\"}").andExpect(status().isOk());
    }

    @Test
    void roleOrActiveIsRejected() throws Exception {
        putMe("{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        putMe("{\"active\":false}").andExpect(status().isForbidden());
        assertThat(alice.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/user/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
