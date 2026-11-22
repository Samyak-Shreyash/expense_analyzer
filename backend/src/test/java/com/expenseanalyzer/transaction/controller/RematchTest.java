package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonPayeeRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Rematching already-imported rows: only unlabelled rows change, and repeating it changes nothing. */
@SpringBootTest
class RematchTest {

    private static final String ALICE = "alice@example.com";
    
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private PersonPayeeRepository personPayeeRepository;
    @Autowired
    private EffectiveCategoryResolver categoryResolver;

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
        alice.setFullName(ALICE);
        alice.setRole(UserRole.USER);
        when(userRepository.findByEmail(ALICE)).thenReturn(Optional.of(alice));
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        personPayeeRepository.deleteAll();
        personRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
    }

    private UUID transaction(String raw, UUID categoryId) {
        Transaction t = new Transaction();
        t.setRawDescription(raw);
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(alice.getId());
        t.setCategoryId(categoryId);
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return transactionRepository.saveAndFlush(t).getId();
    }

    private String summary() throws Exception {
        return mockMvc.perform(get("/api/analytics/summary?from=" + LocalDate.now().minusDays(1) + "&to=" + LocalDate.now().plusDays(1)).header("Authorization", bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void rematch() throws Exception {
        mockMvc.perform(post("/api/transactions/rematch").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unmatchedRowGainsMerchantAndLabelledRowIsUntouched() throws Exception {
        UUID mine = categoryRepository.saveAndFlush(new Category(alice.getId(), "Mine", true)).getId();
        UUID unmatched = transaction("POS KFC OUTLET 123", null);
        UUID labelled = transaction("POS KFC OUTLET 456", mine);

        rematch();

        Transaction row = transactionRepository.findById(unmatched).orElseThrow();
        assertThat(row.getMerchantId()).isNotNull();
        assertThat(merchantRepository.findById(row.getMerchantId()).orElseThrow().getCanonicalName()).isEqualTo("KFC");
        Transaction kept = transactionRepository.findById(labelled).orElseThrow();
        assertThat(kept.getMerchantId()).isNull();
        assertThat(kept.getCategoryId()).isEqualTo(mine);
    }

    @Test
    void secondRematchChangesNothing() throws Exception {
        transaction("POS KFC OUTLET 123", null);
        transaction("SOMETHING UNKNOWN 9", null);

        rematch();
        String after = summary();
        long merchants = merchantRepository.count();
        rematch();

        assertThat(summary()).isEqualTo(after);
        assertThat(merchantRepository.count()).isEqualTo(merchants);
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/transactions/rematch")).andExpect(status().isUnauthorized());
    }

    @Test
    void selfMarkingLeavesUserUnmarkedRowsAlone() {
        UUID self = categoryRepository.save(new Category(alice.getId(), CategoryService.SELF, false)).getId();
        Transaction unmarked = transactionRepository.findById(transaction("TO SELF", self)).orElseThrow();
        unmarked.setTransferManual(true);
        Transaction fresh = transactionRepository.findById(transaction("TO SELF", self)).orElseThrow();

        categoryResolver.markSelfAsTransfers(alice.getId(), List.of(unmarked, fresh));

        assertThat(unmarked.isTransfer()).isFalse();
        assertThat(fresh.isTransfer()).isTrue();
    }
}
