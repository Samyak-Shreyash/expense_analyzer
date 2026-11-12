package com.expenseanalyzer.security;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.model.PersonPayee;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonPayeeRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementService;
import com.expenseanalyzer.statement.service.S3StatementStorage;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private PersonRepository personRepository;
    @Autowired
    private PersonPayeeRepository personPayeeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private StatementService statementService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private SpendingAnalyticsService analyticsService;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private S3StatementStorage storage;

    private MockMvc mockMvc;
    private UUID alice;
    private UUID bob;
    private UUID bobStatement;
    private UUID bobTransaction;
    private UUID bobMerchant;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);

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
        personPayeeRepository.deleteAll();
        personRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    /** No row is needed while UserRepository is mocked; the Postgres subclass writes one for its foreign keys. */
    protected void persistUser(UUID id) {
    }

    private UUID user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        persistUser(u.getId());
        return u.getId();
    }

    private String aliceToken() {
        return token(ALICE);
    }

    private String token(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private void expectNotFoundForAlice(MockHttpServletRequestBuilder request, String json) throws Exception {
        mockMvc.perform(request.header("Authorization", aliceToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    private String analytics(String path, String token) throws Exception {
        return mockMvc.perform(get("/api/analytics/" + path).param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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

    @Test
    void remainingAnalyticsNeverIncludeOtherUsersData() throws Exception {
        Category loan = categoryRepository.save(new Category(bob, "Secret Loan", false));
        Transaction t = new Transaction(bob, "Bob's loan", new BigDecimal("777000"),
                LocalDate.of(2026, 1, 10).atStartOfDay(ZoneOffset.UTC).toInstant(), "DEBIT");
        t.setCategoryId(loan.getId());
        transactionRepository.save(t);

        for (String path : List.of("categories", "cash-flow", "trends", "non-spending")) {
            assertThat(analytics(path, aliceToken()))
                    .doesNotContain("1250").doesNotContain("7770").doesNotContain("9020").doesNotContain("Secret Loan");
        }
        // the same requests do show Bob his own data, so the checks above are not vacuous
        assertThat(analytics("categories", token(BOB))).contains("1250");
        assertThat(analytics("cash-flow", token(BOB))).contains("9020");
        assertThat(analytics("trends", token(BOB))).contains("1250");
        assertThat(analytics("non-spending", token(BOB))).contains("7770");
    }

    // ---- HTTP: people ----

    @Test
    void foreignPersonCannotBeChanged() throws Exception {
        Person bobs = personRepository.save(new Person(bob, "Bob Friend"));
        personPayeeRepository.save(new PersonPayee(bob, "BOB FRIEND", bobs.getId()));
        Person mine = personRepository.save(new Person(alice, "Mine"));
        Category own = categoryRepository.save(new Category(alice, "Pets", true));

        expectNotFoundForAlice(put("/api/people/" + bobs.getId() + "/category"),
                "{\"categoryId\":\"" + own.getId() + "\"}");
        expectNotFoundForAlice(post("/api/people/" + bobs.getId() + "/merge"),
                "{\"intoId\":\"" + mine.getId() + "\"}");
        expectNotFoundForAlice(put("/api/people/" + bobs.getId() + "/payees"),
                "{\"payee\":\"BOB FRIEND\",\"personId\":\"" + mine.getId() + "\"}");

        assertThat(personRepository.findById(bobs.getId()).orElseThrow().getCategoryId()).isNull();
        assertThat(personPayeeRepository.findById(new PersonPayee.Key(bob, "BOB FRIEND")).orElseThrow().getPersonId())
                .isEqualTo(bobs.getId());
    }

    @Test
    void ownPersonCannotAdoptForeignCategoryOrMoveToForeignPerson() throws Exception {
        Person mine = personRepository.save(new Person(alice, "Mine"));
        personPayeeRepository.save(new PersonPayee(alice, "MINE", mine.getId()));
        Person bobs = personRepository.save(new Person(bob, "Bob Friend"));
        Category bobsCategory = categoryRepository.save(new Category(bob, "Secret", true));

        expectNotFoundForAlice(put("/api/people/" + mine.getId() + "/category"),
                "{\"categoryId\":\"" + bobsCategory.getId() + "\"}");
        expectNotFoundForAlice(put("/api/people/" + mine.getId() + "/payees"),
                "{\"payee\":\"MINE\",\"personId\":\"" + bobs.getId() + "\"}");

        assertThat(personRepository.findById(mine.getId()).orElseThrow().getCategoryId()).isNull();
        assertThat(personPayeeRepository.findById(new PersonPayee.Key(alice, "MINE")).orElseThrow().getPersonId())
                .isEqualTo(mine.getId());
    }

    // ---- HTTP: admin ----

    @Test
    void adminHasNoAccessToOtherUsersFinancialData() throws Exception {
        String email = "admin@example.com";
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail(email);
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(admin));
        String token = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/statements/" + bobStatement).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/transactions/" + bobTransaction).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/statements/" + bobStatement).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/transactions").param("statementId", bobStatement.toString())
                        .header("Authorization", token))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.transactionCount").value(0));

        assertThat(statementRepository.existsById(bobStatement)).isTrue();
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
