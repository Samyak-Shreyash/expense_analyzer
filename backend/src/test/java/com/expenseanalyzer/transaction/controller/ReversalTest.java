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

/** A failed payment paired with the refund that reversed it: pairing rules and their effect on analytics. */
@SpringBootTest
class ReversalTest {

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

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private UUID account;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        account = statementRepository.saveAndFlush(new Statement(alice.getId(), "1111", "AXIS")).getId();
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

    private UUID tx(User owner, String type, String amount) {
        Transaction t = new Transaction();
        t.setRawDescription("row");
        t.setAmountCents(new BigDecimal(amount).movePointRight(2));
        t.setDate(JAN_10);
        t.setType(type);
        t.setUserId(owner.getId());
        t.setStatementId(account);
        t.setCreatedAt(JAN_10);
        t.setUpdatedAt(JAN_10);
        return transactionRepository.saveAndFlush(t).getId();
    }

    private Transaction reload(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private void pair(String email, UUID id, UUID with, int expected) throws Exception {
        String body = with == null ? "{\"pairWithId\":null}" : "{\"pairWithId\":\"" + with + "\"}";
        mockMvc.perform(put("/api/transactions/" + id + "/reversal").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    @Test
    void aPairedFailedPaymentAndItsRefundAreLeftOutOfAnalytics() throws Exception {
        UUID failed = tx(alice, "DEBIT", "50000.00");
        UUID refund = tx(alice, "CREDIT", "50000.00");
        tx(alice, "DEBIT", "300.00");

        mockMvc.perform(put("/api/transactions/" + failed + "/reversal").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pairWithId\":\"" + refund + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalPairId").value(refund.toString()));

        assertThat(reload(refund).getReversalPairId()).isEqualTo(failed);
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflow").value(0))
                .andExpect(jsonPath("$.outflow").value(300.00))
                .andExpect(jsonPath("$.transactionCount").value(1));
    }

    @Test
    void chargesOnARefundedPaymentStayInSpendingUntilUnpaired() throws Exception {
        UUID failed = tx(alice, "DEBIT", "1000.00");
        UUID refund = tx(alice, "CREDIT", "980.00");

        pair(ALICE, refund, failed, 200);

        assertThat(reload(failed).getReversalChargePaise()).isEqualByComparingTo("2000");
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.inflow").value(0))
                .andExpect(jsonPath("$.outflow").value(20.00));

        pair(ALICE, refund, null, 200);

        assertThat(reload(failed).getReversalChargePaise()).isNull();
    }

    @Test
    void unpairingReleasesBothHalves() throws Exception {
        UUID failed = tx(alice, "DEBIT", "50000.00");
        UUID refund = tx(alice, "CREDIT", "50000.00");
        pair(ALICE, failed, refund, 200);

        pair(ALICE, refund, null, 200);

        assertThat(reload(failed).getReversalPairId()).isNull();
        assertThat(reload(refund).getReversalPairId()).isNull();
    }

    @Test
    void repairingReleasesTheEarlierPartner() throws Exception {
        UUID failed = tx(alice, "DEBIT", "50000.00");
        UUID wrong = tx(alice, "CREDIT", "50000.00");
        UUID right = tx(alice, "CREDIT", "50000.00");
        pair(ALICE, failed, wrong, 200);

        pair(ALICE, failed, right, 200);

        assertThat(reload(wrong).getReversalPairId()).isNull();
        assertThat(reload(right).getReversalPairId()).isEqualTo(failed);
    }

    @Test
    void pairingClearsATransferMark() throws Exception {
        UUID failed = tx(alice, "DEBIT", "50000.00");
        UUID refund = tx(alice, "CREDIT", "50000.00");
        Transaction t = reload(failed);
        t.setTransfer(true);
        transactionRepository.saveAndFlush(t);

        pair(ALICE, failed, refund, 200);

        assertThat(reload(failed).isTransfer()).isFalse();
        assertThat(reload(failed).isTransferManual()).isTrue();
    }

    @Test
    void rejectsSameDirectionDifferentAmountsAndOtherUsersRows() throws Exception {
        UUID failed = tx(alice, "DEBIT", "50000.00");
        UUID sameWay = tx(alice, "DEBIT", "50000.00");
        UUID smaller = tx(alice, "CREDIT", "44000.00");
        UUID larger = tx(alice, "CREDIT", "50001.00");
        UUID bobs = tx(bob, "CREDIT", "50000.00");

        pair(ALICE, failed, sameWay, 400);
        pair(ALICE, failed, smaller, 400);
        pair(ALICE, failed, larger, 400);
        pair(ALICE, failed, bobs, 404);
        pair(BOB, failed, bobs, 404);
        assertThat(reload(failed).getReversalPairId()).isNull();
    }
}
