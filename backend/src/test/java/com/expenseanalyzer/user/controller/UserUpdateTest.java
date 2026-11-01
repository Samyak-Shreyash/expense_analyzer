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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserUpdateTest {

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
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from preferences");
    }

    private ResultActions update(String body) throws Exception {
        String token = jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        return mockMvc.perform(put("/api/user/" + alice.getId()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validNameUpdateWorks() throws Exception {
        update("{\"fullName\":\"Alice B\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice B"));
    }

    @Test
    void emailChangeIsRejected() throws Exception {
        update("{\"email\":\"new@example.com\"}").andExpect(status().isBadRequest());
        assertThat(alice.getEmail()).isEqualTo(ALICE);
    }

    @Test
    void invalidPreferencesAreBadRequest() throws Exception {
        for (String prefs : new String[] {"{\"theme\":\"neon\"}", "{\"currency\":\"XXX\"}",
                "{\"notifications\":\"yes\"}", "{\"theme\":null}", "{\"unknown\":\"x\"}"}) {
            update("{\"preferences\":" + prefs + "}").andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForList("select id from preferences")).isEmpty();
    }

    @Test
    void validPreferencesArePersisted() throws Exception {
        update("{\"preferences\":{\"theme\":\"dark\",\"currency\":\"inr\",\"notifications\":false}}")
                .andExpect(status().isOk());
        // raw SQL: the jsonb value column does not round-trip through Hibernate on H2
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select \"key\" k, theme, currency, notification_mode, user_id from preferences");
        assertThat(rows).hasSize(3).allSatisfy(r -> assertThat(r.get("USER_ID").toString())
                .isEqualTo(alice.getId().toString()));
        assertThat(rows).extracting(r -> r.get("K")).containsExactlyInAnyOrder("theme", "currency", "notifications");
        assertThat(rows).extracting(r -> r.get("THEME")).contains("DARK");
        assertThat(rows).extracting(r -> r.get("CURRENCY")).contains("INR");
        assertThat(rows).extracting(r -> r.get("NOTIFICATION_MODE")).contains("NONE");
    }
}
