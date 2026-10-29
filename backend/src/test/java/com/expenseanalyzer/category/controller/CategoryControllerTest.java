package com.expenseanalyzer.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CategoryControllerTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        user(BOB);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private static String body(String name, boolean spends) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ",\"countsAsSpending\":" + spends + "}";
    }

    private String createAs(String email, String name) throws Exception {
        String json = mockMvc.perform(post("/api/categories").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void listSeedsDefaultsOnceSortedByName() throws Exception {
        int expected = MerchantNormalizer.defaultCategoryNames().size() + 2;
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/categories").header("Authorization", bearer(ALICE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(expected));
        }
        assertThat(categoryRepository.findByUserIdOrderByNameAsc(alice.getId())).hasSize(expected);
        mockMvc.perform(get("/api/categories").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$[?(@.name=='Loan')].countsAsSpending").value(false))
                .andExpect(jsonPath("$[?(@.name=='Groceries')].countsAsSpending").value(true))
                .andExpect(jsonPath("$[0].name").value("Entertainment"));
    }

    @Test
    void createRenameToggleAndDelete() throws Exception {
        String id = createAs(ALICE, "  Pets ");
        mockMvc.perform(put("/api/categories/" + id).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Pet care", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pet care"))
                .andExpect(jsonPath("$.countsAsSpending").value(false));
        mockMvc.perform(delete("/api/categories/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());
        assertThat(categoryRepository.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    void createTrimsName() throws Exception {
        mockMvc.perform(post("/api/categories").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("  Pets ", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pets"));
    }

    @Test
    void duplicateNameIsConflictCaseInsensitively() throws Exception {
        String id = createAs(ALICE, "Pets");
        mockMvc.perform(post("/api/categories").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("PETS", true)))
                .andExpect(status().isConflict());
        String other = createAs(ALICE, "Gifts");
        mockMvc.perform(put("/api/categories/" + other).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pets", true)))
                .andExpect(status().isConflict());
        // renaming to its own name (different case) is fine
        mockMvc.perform(put("/api/categories/" + id).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pets", true)))
                .andExpect(status().isOk());
        // another user may reuse the name
        createAs(BOB, "Pets");
    }

    @Test
    void invalidNamesAreBadRequest() throws Exception {
        for (String name : new String[] {null, "", "   ", "x".repeat(51), "Uncategorized", " uncategorized "}) {
            mockMvc.perform(post("/api/categories").header("Authorization", bearer(ALICE))
                            .contentType(MediaType.APPLICATION_JSON).content(body(name, true)))
                    .andExpect(status().isBadRequest());
        }
        String id = createAs(ALICE, "Pets");
        mockMvc.perform(put("/api/categories/" + id).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("UNCATEGORIZED", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersCategoryIsNotFoundAndHidden() throws Exception {
        String id = createAs(BOB, "Secret");
        mockMvc.perform(put("/api/categories/" + id).header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Mine", true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/categories/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/categories").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$[?(@.name=='Secret')]").isEmpty());
    }

    @Test
    void deleteClearsTransactionCategory() throws Exception {
        String id = createAs(ALICE, "Pets");
        Transaction t = new Transaction();
        t.setRawDescription("PET SHOP");
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(alice.getId());
        t.setCategoryId(UUID.fromString(id));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        UUID txId = transactionRepository.saveAndFlush(t).getId();

        mockMvc.perform(delete("/api/categories/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.findById(txId).orElseThrow().getCategoryId()).isNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }
}
