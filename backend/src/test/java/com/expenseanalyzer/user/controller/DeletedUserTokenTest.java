package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.config.RateLimiter;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the real UserRepository on the real schema, so the token check reads the row the deletion actually wrote. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=POSTGRESQL"
})
class DeletedUserTokenTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    private static final String EMAIL = "deleted-token@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private S3StatementStorage storage;
    @MockitoBean
    private RateLimiter rateLimiter;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void accessTokenStopsWorkingOnceTheAccountIsDeleted() throws Exception {
        User user = new User(EMAIL, passwordEncoder.encode("correct-password-1"));
        user.setRole(UserRole.USER);
        userRepository.save(user);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String token = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
        mvc.perform(get("/api/user/me").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(delete("/api/user/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-password-1\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/user/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
}
