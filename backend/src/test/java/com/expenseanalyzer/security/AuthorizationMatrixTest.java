package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthorizationMatrixTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String CAROL = "carol@example.com";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PreferenceRepository preferenceRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE, "Alice", UserRole.USER);
        bob = user(BOB, "Bob", UserRole.USER);
        carol = user(CAROL, "Carol", UserRole.ADMIN);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private MockHttpServletRequestBuilder putUser(UUID id, String email, String body) {
        return put("/api/user/" + id).header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void userReadsAndUpdatesOnlyOwnData() throws Exception {
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ALICE));
        mockMvc.perform(putUser(alice.getId(), ALICE, "{\"fullName\":\"Alice B\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void meNeverReturnsAnotherUsersData() throws Exception {
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(BOB));
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ALICE));
    }

    @Test
    void userCannotUpdateAnotherUser() throws Exception {
        mockMvc.perform(putUser(bob.getId(), ALICE, "{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(putUser(alice.getId(), BOB, "{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());

        mockMvc.perform(putUser(bob.getId(), BOB, "{\"fullName\":\"X\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleInDbDoesNotGrantUpdateOfOthers() throws Exception {
        // Documents current behaviour: no admin override on user updates.
        mockMvc.perform(putUser(alice.getId(), CAROL, "{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());

        mockMvc.perform(putUser(carol.getId(), CAROL, "{\"fullName\":\"X\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotChangeOwnRoleOrActive() throws Exception {
        for (String body : new String[] {
                "{\"role\":\"ADMIN\"}", "{\"role\":\"USER\"}", "{\"active\":false}", "{\"active\":true}" }) {
            mockMvc.perform(putUser(alice.getId(), ALICE, body)).andExpect(status().isForbidden());
        }
        verify(userRepository, never()).save(any());

        mockMvc.perform(putUser(alice.getId(), ALICE, "{\"fullName\":\"Ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownUserIdIs404() throws Exception {
        mockMvc.perform(putUser(UUID.randomUUID(), ALICE, "{\"fullName\":\"X\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(putUser(alice.getId(), ALICE, "{\"fullName\":\"X\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        String path = "/api/user/" + alice.getId();
        String body = "{\"fullName\":\"X\"}";
        mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/me").header("Authorization", "Basic YWxpY2U6cGFzc3dvcmQ="))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(path).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/user/me").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());
    }

    @Test
    void authPathsStayPublic() throws Exception {
        mockMvc.perform(get("/api/auth/register/check-email").param("email", "new@example.com"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonexistentAdminPathIsNotPublic() throws Exception {
        // TODO: expect 403 for USER once admin endpoints and role rules land; today it fails closed.
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(ALICE)))
                .andExpect(status().isNotFound());
    }
}
