package com.expenseanalyzer.security;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementService;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.service.TransactionService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolation matrix (SEC-006): Alice must not read, modify or infer Bob's statements, transactions or
 * analytics, by guessed ids, altered paths/query parameters, or by calling services directly.
 */
@SpringBootTest
class CrossUserIsolationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private StatementService statementService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private SpendingAnalyticsService analyticsService;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private StatementStorage storage;

    private MockMvc mockMvc;
    private UUID alice;
    private UUID bobStatement;
    private UUID bobTransaction;
    private UUID bobMerchant;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        UUID bob = user(BOB);

        Statement s = new Statement();
        s.setUserId(bob);
        s.setActive(true);
        s.setFormat(StatementFormat.CSV);
        s.setStatus(StatementStatus.COMPLETED);
        s.setStorageKey("users/" + bob + "/statements/x");
        s.onCreate();
        bobStatement = statementRepository.save(s).getId();

        bobMerchant = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
        Transaction t = new Transaction(bob, "Bob's dinner", new BigDecimal("125000"),
                LocalDate.of(2026, 1, 10).atStartOfDay(ZoneOffset.UTC).toInstant(), "DEBIT");
        t.setStatementId(bobStatement);
        t.setMerchantId(bobMerchant);
        bobTransaction = transactionRepository.save(t).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    private UUID user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u.getId();
    }

    private String aliceToken() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
    }

    // ---- HTTP: guessed and foreign ids ----

    @Test
    void foreignStatementCannotBeReadOrDeleted() throws Exception {
        mockMvc.perform(get("/api/statements/" + bobStatement).header("Authorization", aliceToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/statements/" + bobStatement + "/status").header("Authorization", aliceToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/statements/" + bobStatement).header("Authorization", aliceToken()))
                .andExpect(status().isNotFound());

        assertThat(statementRepository.existsById(bobStatement)).isTrue();
        assertThat(transactionRepository.existsById(bobTransaction)).isTrue();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void foreignTransactionCannotBeRead() throws Exception {
        mockMvc.perform(get("/api/transactions/" + bobTransaction).header("Authorization", aliceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignIdIsIndistinguishableFromAGuessedOne() throws Exception {
        MvcResult foreign = mockMvc.perform(get("/api/transactions/" + bobTransaction)
                .header("Authorization", aliceToken())).andReturn();
        MvcResult guessed = mockMvc.perform(get("/api/transactions/" + UUID.randomUUID())
                .header("Authorization", aliceToken())).andReturn();

        assertThat(foreign.getResponse().getStatus()).isEqualTo(guessed.getResponse().getStatus());
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(guessed.getResponse().getContentAsString());
    }

    // ---- HTTP: altered query parameters ----

    @Test
    void filteringByForeignStatementOrMerchantReturnsNothing() throws Exception {
        mockMvc.perform(get("/api/transactions").param("statementId", bobStatement.toString())
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/transactions").param("merchantId", bobMerchant.toString())
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void injectedUserIdParameterIsIgnored() throws Exception {
        mockMvc.perform(get("/api/transactions").param("userId", userRepository.findByEmail(BOB).orElseThrow().getId().toString())
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/statements").param("userId", userRepository.findByEmail(BOB).orElseThrow().getId().toString())
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void analyticsNeverIncludeOtherUsersData() throws Exception {
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.outflow").value(0));
        mockMvc.perform(get("/api/analytics/merchants").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", aliceToken()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ---- direct service-layer access ----

    @Test
    void servicesScopeEveryLookupToTheCaller() {
        assertThat(statementService.find(ALICE, bobStatement)).isEmpty();
        assertThat(statementService.view(ALICE, bobStatement)).isEmpty();
        assertThat(statementService.delete(ALICE, bobStatement)).isFalse();
        assertThat(transactionService.find(ALICE, bobTransaction)).isEmpty();
        assertThat(analyticsService.summary(ALICE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                .transactionCount()).isZero();
        assertThat(statementRepository.existsById(bobStatement)).isTrue();
        assertThat(alice).isNotNull();
    }
}
