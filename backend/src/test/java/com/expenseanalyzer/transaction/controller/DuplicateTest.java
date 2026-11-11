package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementService;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
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

/** Rows the user marks as duplicates: kept, but left out of analytics. */
@SpringBootTest
class DuplicateTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final Instant JAN_10 = Instant.parse("2026-01-10T00:00:00Z");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private StatementService statementService;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private UUID savings;
    private UUID current;
    /** Strictly increasing creation times, so "earliest created" ties are deterministic. */
    private Instant created = Instant.parse("2026-02-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        savings = statement(alice, "1111", "HDFC");
        current = statement(alice, "2222", "SBI");
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private UUID statement(User owner, String accountNumber, String bank) {
        return statementRepository.saveAndFlush(new Statement(owner.getId(), accountNumber, bank)).getId();
    }

    private UUID tx(User owner, UUID statementId, String type, String amount, Instant date) {
        Transaction t = new Transaction();
        t.setRawDescription("row");
        t.setAmountCents(new BigDecimal(amount).movePointRight(2));
        t.setDate(date);
        t.setType(type);
        t.setUserId(owner.getId());
        t.setStatementId(statementId);
        created = created.plusSeconds(1);
        t.setCreatedAt(created);
        t.setUpdatedAt(created);
        return transactionRepository.saveAndFlush(t).getId();
    }

    private Transaction reload(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private void mark(String email, UUID id, boolean duplicate, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + id + "/duplicate").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content("{\"duplicate\":" + duplicate + "}"))
                .andExpect(status().is(expected));
    }

    @Test
    void markingAndUnmarkingADuplicate() throws Exception {
        UUID id = tx(alice, savings, "DEBIT", "300.00", JAN_10);
        mockMvc.perform(put("/api/transactions/" + id + "/duplicate").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"duplicate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        assertThat(reload(id).isDuplicate()).isTrue();

        mark(ALICE, id, false, 200);
        assertThat(reload(id).isDuplicate()).isFalse();
    }

    @Test
    void duplicatesAreLeftOutOfAnalytics() throws Exception {
        UUID copy = tx(alice, savings, "DEBIT", "300.00", JAN_10);
        tx(alice, current, "DEBIT", "300.00", JAN_10);
        mark(ALICE, copy, true, 200);

        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outflow").value(300.00))
                .andExpect(jsonPath("$.transactionCount").value(1));
        mockMvc.perform(get("/api/transactions").param("duplicate", "true").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void anotherUsersTransactionIs404() throws Exception {
        UUID id = tx(alice, savings, "DEBIT", "300.00", JAN_10);
        mark(BOB, id, true, 404);
        assertThat(reload(id).isDuplicate()).isFalse();
    }

    @Test
    void aPairedTransactionMustBeUnpairedFirst() throws Exception {
        UUID id = tx(alice, savings, "DEBIT", "300.00", JAN_10);
        Transaction t = reload(id);
        t.setTransferPairId(UUID.randomUUID());
        transactionRepository.saveAndFlush(t);
        mark(ALICE, id, true, 400);
        assertThat(reload(id).isDuplicate()).isFalse();
    }
}
