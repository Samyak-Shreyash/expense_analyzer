package com.expenseanalyzer.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Default categories are seeded once per user (real users, real repository). */
@SpringBootTest
class CategorySeedingTest {

    private static final String EMAIL = "seed-user@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // plain JDBC with NULL preferences: the H2 test schema cannot round-trip the users.preferences JSON column
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, is_active, role, created_at, updated_at, categories_seeded)"
                + " VALUES (?, ?, 'hash', TRUE, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)", userId, EMAIL);
    }

    @AfterEach
    void cleanUp() {
        categoryRepository.deleteAll();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
    }

    private void list() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", bearer())).andExpect(status().isOk());
    }

    private boolean seeded() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT categories_seeded FROM users WHERE id = ?", Boolean.class, userId));
    }

    private List<Category> categories() {
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    private Category named(String name) {
        return categories().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void firstListSeedsOnceAndSetsFlag() throws Exception {
        assertThat(seeded()).isFalse();
        list();
        int seeded = categories().size();
        assertThat(seeded).isGreaterThan(0);
        assertThat(seeded()).isTrue();

        list();
        assertThat(categories()).hasSize(seeded);
    }

    @Test
    void deletedDefaultDoesNotComeBack() throws Exception {
        list();
        mockMvc.perform(delete("/api/categories/" + named("Loan").getId()).header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        list();
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                .header("Authorization", bearer())).andExpect(status().isOk());

        assertThat(categories()).noneMatch(c -> c.getName().equals("Loan"));
    }

    @Test
    void renamedDefaultIsNotRecreated() throws Exception {
        list();
        mockMvc.perform(put("/api/categories/" + named("Shopping").getId()).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Purchases\",\"countsAsSpending\":true}"))
                .andExpect(status().isOk());

        list();

        assertThat(categories()).noneMatch(c -> c.getName().equals("Shopping"))
                .anyMatch(c -> c.getName().equals("Purchases"));
    }
}
