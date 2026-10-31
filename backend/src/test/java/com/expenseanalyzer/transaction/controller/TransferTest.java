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
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.service.TransferService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
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

/** Transfers between a user's own accounts: automatic pairing, manual marking and their effect on analytics. */
@SpringBootTest
class TransferTest {

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
    private TransferService transferService;

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

    private void mark(String email, UUID id, String body, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + id + "/transfer").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    @Test
    void pairsMoneyOutAndInAcrossAccountsUpToThreeDaysApart() {
        UUID out = tx(alice, savings, "DEBIT", "5000.00", JAN_10);
        UUID in = tx(alice, current, "CREDIT", "5000.00", JAN_10.plus(Duration.ofDays(3)));
        UUID lateOut = tx(alice, savings, "DEBIT", "700.00", JAN_10);
        UUID lateIn = tx(alice, current, "CREDIT", "700.00", JAN_10.plus(Duration.ofDays(4)));

        transferService.match(alice.getId());

        assertThat(reload(out).isTransfer()).isTrue();
        assertThat(reload(out).getTransferPairId()).isEqualTo(in);
        assertThat(reload(in).getTransferPairId()).isEqualTo(out);
        assertThat(reload(lateOut).isTransfer()).isFalse();
        assertThat(reload(lateIn).isTransfer()).isFalse();
    }

    @Test
    void neverPairsWithinOneAccountOrAcrossUsersOrDifferentAmounts() {
        UUID out = tx(alice, savings, "DEBIT", "5000.00", JAN_10);
        UUID sameAccount = tx(alice, savings, "CREDIT", "5000.00", JAN_10);
        UUID otherAmount = tx(alice, current, "CREDIT", "4999.00", JAN_10);
        UUID bobs = tx(bob, statement(bob, "3333", "ICICI"), "CREDIT", "5000.00", JAN_10);

        transferService.match(alice.getId());
        transferService.match(bob.getId());

        for (UUID id : new UUID[] {out, sameAccount, otherAmount, bobs}) {
            assertThat(reload(id).isTransfer()).isFalse();
        }
    }

    @Test
    void closestDateWinsThenEarliestCreatedAndPairedRowsAreKept() {
        UUID out = tx(alice, savings, "DEBIT", "100.00", JAN_10);
        UUID far = tx(alice, current, "CREDIT", "100.00", JAN_10.plus(Duration.ofDays(2)));
        UUID near = tx(alice, current, "CREDIT", "100.00", JAN_10.plus(Duration.ofDays(1)));
        UUID tieFirst = tx(alice, savings, "DEBIT", "200.00", JAN_10);
        UUID creditA = tx(alice, current, "CREDIT", "200.00", JAN_10.plus(Duration.ofDays(1)));
        UUID creditB = tx(alice, current, "CREDIT", "200.00", JAN_10.minus(Duration.ofDays(1)));

        transferService.match(alice.getId());
        assertThat(reload(out).getTransferPairId()).isEqualTo(near);
        assertThat(reload(far).isTransfer()).isFalse();
        assertThat(reload(tieFirst).getTransferPairId()).isEqualTo(creditA); // equal gap: created first

        // A new candidate does not steal an existing pair.
        tx(alice, current, "CREDIT", "100.00", JAN_10);
        transferService.match(alice.getId());
        assertThat(reload(out).getTransferPairId()).isEqualTo(near);
        assertThat(reload(creditB).isTransfer()).isFalse();
    }

    @Test
    void manualMarkingIsKeptAndUnmarkingAPairUnmarksBothForGood() throws Exception {
        UUID single = tx(alice, savings, "DEBIT", "900.00", JAN_10);
        mark(ALICE, single, "{\"transfer\":true}", 200);
        assertThat(reload(single).isTransfer()).isTrue();
        assertThat(reload(single).getTransferPairId()).isNull();

        UUID out = tx(alice, savings, "DEBIT", "5000.00", JAN_10);
        UUID in = tx(alice, current, "CREDIT", "5000.00", JAN_10);
        transferService.match(alice.getId());
        mark(ALICE, out, "{\"transfer\":false}", 200);
        transferService.match(alice.getId());
        assertThat(reload(out).isTransfer()).isFalse();
        assertThat(reload(in).isTransfer()).isFalse();

        mockMvc.perform(put("/api/transactions/" + in + "/transfer").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transfer\":true,\"pairWithId\":\"" + out + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer").value(true))
                .andExpect(jsonPath("$.transferPairId").value(out.toString()));
        assertThat(reload(out).getTransferPairId()).isEqualTo(in);
    }

    @Test
    void manualPairMustBeOppositeDirectionSameAmountAndOwned() throws Exception {
        UUID out = tx(alice, savings, "DEBIT", "5000.00", JAN_10);
        UUID otherOut = tx(alice, current, "DEBIT", "5000.00", JAN_10);
        UUID otherAmount = tx(alice, current, "CREDIT", "10.00", JAN_10);
        UUID bobs = tx(bob, null, "CREDIT", "5000.00", JAN_10);

        mark(ALICE, out, "{\"transfer\":true,\"pairWithId\":\"" + otherOut + "\"}", 400);
        mark(ALICE, out, "{\"transfer\":true,\"pairWithId\":\"" + otherAmount + "\"}", 400);
        mark(ALICE, out, "{\"transfer\":true,\"pairWithId\":\"" + bobs + "\"}", 404);
        mark(BOB, out, "{\"transfer\":true}", 404);
        assertThat(reload(out).isTransfer()).isFalse();
    }

    @Test
    void transfersAreLeftOutOfAnalyticsAndReportedSeparately() throws Exception {
        UUID out = tx(alice, savings, "DEBIT", "5000.00", JAN_10);
        tx(alice, current, "CREDIT", "5000.00", JAN_10);
        tx(alice, savings, "DEBIT", "300.00", JAN_10);
        tx(alice, current, "CREDIT", "1000.00", JAN_10.plus(Duration.ofDays(10)));
        transferService.match(alice.getId());

        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflow").value(1000.00))
                .andExpect(jsonPath("$.outflow").value(300.00))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.transfersIn").value(5000.00))
                .andExpect(jsonPath("$.transfersOut").value(5000.00));
        mockMvc.perform(get("/api/transactions").param("transfer", "true").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(get("/api/transactions/" + out).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.transfer").value(true));
    }
}
