package com.expenseanalyzer.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
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

/** Merchant/transaction category assignment and the effective category it produces. */
@SpringBootTest
class CategoryAssignmentTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserMerchantCategoryRepository mappingRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private UUID swiggy;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        swiggy = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        mappingRepository.deleteAll();
        categoryRepository.deleteAll();
        merchantRepository.deleteAll();
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

    private String createCategory(String email, String name) throws Exception {
        String json = mockMvc.perform(post("/api/categories").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"countsAsSpending\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private UUID transaction(User owner, String desc, UUID merchantId) {
        Transaction t = new Transaction();
        t.setRawDescription(desc);
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(owner.getId());
        t.setMerchantId(merchantId);
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return transactionRepository.saveAndFlush(t).getId();
    }

    private static String assignment(String categoryId) {
        return "{\"categoryId\":" + (categoryId == null ? "null" : "\"" + categoryId + "\"") + "}";
    }

    private void assignMerchant(String email, UUID merchantId, String categoryId) throws Exception {
        mockMvc.perform(put("/api/merchants/" + merchantId + "/category").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(categoryId))).andExpect(status().isNoContent());
    }

    private void assignTransaction(String email, UUID id, String categoryId) throws Exception {
        mockMvc.perform(put("/api/transactions/" + id + "/category").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(categoryId))).andExpect(status().isOk());
    }

    @Test
    void merchantCategoryOverridesHintAndCoversLaterTransactionsAndClearsBack() throws Exception {
        UUID first = transaction(alice, "first", swiggy);
        String pets = createCategory(ALICE, "Pets");
        mockMvc.perform(get("/api/transactions/" + first).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Food & Dining"))
                .andExpect(jsonPath("$.categoryOverridden").value(false));

        assignMerchant(ALICE, swiggy, pets);
        UUID later = transaction(alice, "imported later", swiggy);
        for (UUID id : new UUID[] {first, later}) {
            mockMvc.perform(get("/api/transactions/" + id).header("Authorization", bearer(ALICE)))
                    .andExpect(jsonPath("$.category").value("Pets"))
                    .andExpect(jsonPath("$.categoryId").value(pets))
                    .andExpect(jsonPath("$.categoryOverridden").value(false));
        }

        assignMerchant(ALICE, swiggy, null);
        mockMvc.perform(get("/api/transactions/" + first).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Food & Dining"));
    }

    @Test
    void transactionOverrideWinsOverMerchantCategoryAndClearsBack() throws Exception {
        UUID overridden = transaction(alice, "special", swiggy);
        UUID plain = transaction(alice, "plain", swiggy);
        String pets = createCategory(ALICE, "Pets");
        String gifts = createCategory(ALICE, "Gifts");
        assignMerchant(ALICE, swiggy, pets);

        assignTransaction(ALICE, overridden, gifts);
        mockMvc.perform(get("/api/transactions/" + overridden).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Gifts"))
                .andExpect(jsonPath("$.categoryOverridden").value(true));
        mockMvc.perform(get("/api/transactions/" + plain).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Pets"));

        assignTransaction(ALICE, overridden, null);
        mockMvc.perform(get("/api/transactions/" + overridden).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Pets"))
                .andExpect(jsonPath("$.categoryOverridden").value(false));
    }

    @Test
    void ownershipIsNotFound() throws Exception {
        String bobsCategory = createCategory(BOB, "Secret");
        String mine = createCategory(ALICE, "Pets");
        UUID bobsTx = transaction(bob, "bob", swiggy);
        UUID mineTx = transaction(alice, "mine", swiggy);

        mockMvc.perform(put("/api/transactions/" + mineTx + "/category").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(bobsCategory))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/merchants/" + swiggy + "/category").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(bobsCategory))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/transactions/" + bobsTx + "/category").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(mine))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/merchants/" + UUID.randomUUID() + "/category").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content(assignment(mine))).andExpect(status().isNotFound());
    }

    @Test
    void filtersByEffectiveCategory() throws Exception {
        transaction(alice, "via merchant", swiggy);
        UUID viaOverride = transaction(alice, "via override", swiggy);
        UUID zomato = merchantRepository.save(new Merchant("Zomato", "Food & Dining")).getId();
        transaction(alice, "via hint", zomato);
        transaction(alice, "none", null);
        String pets = createCategory(ALICE, "Pets");
        assignMerchant(ALICE, swiggy, pets);
        assignTransaction(ALICE, viaOverride, pets);
        String token = bearer(ALICE);

        mockMvc.perform(get("/api/transactions").param("category", "Pets").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("via merchant", "via override")));
        mockMvc.perform(get("/api/transactions").param("category", "Food & Dining").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("via hint")));
        mockMvc.perform(get("/api/transactions").param("category", "Uncategorized").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("none")));
    }

    @Test
    void deletingCategoryClearsMerchantMapping() throws Exception {
        String pets = createCategory(ALICE, "Pets");
        assignMerchant(ALICE, swiggy, pets);
        assertThat(mappingRepository.findByUserId(alice.getId())).hasSize(1);

        mockMvc.perform(delete("/api/categories/" + pets).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());

        assertThat(mappingRepository.findByUserId(alice.getId())).isEmpty();
        UUID tx = transaction(alice, "after", swiggy);
        mockMvc.perform(get("/api/transactions/" + tx).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.category").value("Food & Dining"));
    }
}
