package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementService;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.dto.ReversalRequest;
import com.expenseanalyzer.transaction.dto.TransferRequest;
import com.expenseanalyzer.transaction.service.ReversalService;
import com.expenseanalyzer.transaction.service.TransactionService;
import com.expenseanalyzer.transaction.service.TransferService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Refunds and verification payments pair with their debit automatically, and only when the evidence is there. */
@SpringBootTest
class ReversalAutoTest {

    private static final String EMAIL = "owner@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private ReversalService reversalService;
    @Autowired
    private StatementService statementService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private TransactionService transactionService;

    @MockitoBean
    private UserRepository userRepository;

    private final MerchantNormalizer normalizer = new MerchantNormalizer();
    private MockMvc mockMvc;
    private User user;
    private UUID account;
    private Instant created = Instant.parse("2026-12-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setRole(UserRole.USER);
        user.setFullName("Samyak Shreyash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        account = statementRepository.saveAndFlush(new Statement(user.getId(), "1111", "HDFC")).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
    }

    private Transaction tx(UUID statementId, String type, String amount, String description, String date) {
        Transaction t = new Transaction();
        t.setRawDescription(description);
        t.setNormalizedDescription(normalizer.normalize(description).normalizedDescription());
        t.setAmountCents(new BigDecimal(amount).movePointRight(2));
        t.setDate(Instant.parse(date + "T00:00:00Z"));
        t.setType(type);
        t.setUserId(user.getId());
        t.setStatementId(statementId);
        created = created.plusSeconds(1);
        t.setCreatedAt(created);
        t.setUpdatedAt(created);
        return transactionRepository.saveAndFlush(t);
    }

    private Transaction tx(String type, String amount, String description, String date) {
        return tx(account, type, amount, description, date);
    }

    private Transaction reload(Transaction t) {
        return transactionRepository.findById(t.getId()).orElseThrow();
    }

    @Test
    void zomatoRefundPairsWithOrder() throws Exception {
        Transaction order = tx("DEBIT", "377.90", "UPI/P2M/123456789012/ZOMATO/order", "2026-10-28");
        Transaction refund = tx("CREDIT", "377.90", "UPI/P2P/223456789012/ZOMATO ME/refund", "2026-10-30");
        tx("DEBIT", "100.00", "UPI/P2M/323456789012/CHAIWALA/tea", "2026-10-29");

        reversalService.match(user.getId());

        assertThat(reload(order).getReversalPairId()).isEqualTo(refund.getId());
        assertThat(reload(refund).getReversalPairId()).isEqualTo(order.getId());
        assertThat(reload(order).isReversalPending()).isFalse();
        assertThat(reload(order).isTransferManual()).isFalse();
        assertThat(reload(order).getReversalChargePaise()).isNull();
        String token = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-10-01").param("to", "2026-10-31")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outflow").value(100.00));
    }

    @Test
    void rupeeVerificationPairs() {
        Transaction debit = tx("DEBIT", "1.00", "DREAMPLUG", "2026-09-05");
        Transaction credit = tx("CREDIT", "1.00", "CRADJ", "2026-09-06");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isEqualTo(credit.getId());
        assertThat(reload(credit).getReversalPairId()).isEqualTo(debit.getId());
    }

    @Test
    void rupeeTooFarApart() {
        Transaction debit = tx("DEBIT", "1.00", "DREAMPLUG", "2026-09-05");
        Transaction credit = tx("CREDIT", "1.00", "CRADJ", "2026-09-10");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
    }

    @Test
    void differentMerchantsSameAmountDoNotPair() {
        Transaction debit = tx("DEBIT", "80.00", "SWIGGY", "2026-09-05");
        Transaction credit = tx("CREDIT", "80.00", "RAHUL SHARMA", "2026-09-06");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
    }

    @Test
    void refundBeforePaymentDoesNotPair() {
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx("CREDIT", "377.90", "ZOMATO", "2026-09-04");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
    }

    @Test
    void sharedOwnNameTokenDoesNotPair() {
        Transaction debit = tx("DEBIT", "500.00", "UPI/SHREYASH/food", "2026-09-05");
        Transaction credit = tx("CREDIT", "500.00", "UPI/SHREYASH/gift", "2026-09-06");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
    }

    @Test
    void manualRowsUntouched() {
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        debit.setTransferManual(true);
        transactionRepository.saveAndFlush(debit);
        Transaction credit = tx("CREDIT", "377.90", "ZOMATO", "2026-09-06");

        reversalService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
    }

    @Test
    void deleteStatementRematches() {
        UUID other = statementRepository.saveAndFlush(new Statement(user.getId(), "2222", "SBI")).getId();
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx(other, "CREDIT", "377.90", "ZOMATO", "2026-09-06");
        reversalService.match(user.getId());
        assertThat(reload(debit).getReversalPairId()).isEqualTo(credit.getId());

        assertThat(statementService.delete(EMAIL, other)).isTrue();

        assertThat(reload(debit).getReversalPairId()).isNull();
    }

    @Test
    void deleteStatementReleasesManualPartner() {
        UUID other = statementRepository.saveAndFlush(new Statement(user.getId(), "2222", "SBI")).getId();
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx(other, "CREDIT", "377.90", "ZOMATO", "2026-09-06");
        transactionService.setReversal(EMAIL, debit.getId(), new ReversalRequest(credit.getId(), null));
        assertThat(reload(debit).getReversalPairId()).isEqualTo(credit.getId());

        assertThat(statementService.delete(EMAIL, other)).isTrue();

        assertThat(reload(debit).getReversalPairId()).isNull();
    }

    @Test
    void unpairedAutomaticReversalStaysUnpaired() {
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx("CREDIT", "377.90", "ZOMATO", "2026-09-06");
        reversalService.match(user.getId());
        assertThat(reload(debit).getReversalPairId()).isEqualTo(credit.getId());

        transactionService.setReversal(EMAIL, credit.getId(), new ReversalRequest(null, null));
        reversalService.match(user.getId());
        transferService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
        assertThat(reload(debit).isTransferManual()).isTrue();
        assertThat(reload(credit).isTransferManual()).isTrue();
    }

    @Test
    void transferMatchLeavesReversalPairsAlone() {
        UUID other = statementRepository.saveAndFlush(new Statement(user.getId(), "2222", "SBI")).getId();
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx(other, "CREDIT", "377.90", "ZOMATO", "2026-09-06");
        reversalService.match(user.getId());

        transferService.match(user.getId());

        assertThat(reload(debit).getReversalPairId()).isEqualTo(credit.getId());
        assertThat(reload(credit).getReversalPairId()).isEqualTo(debit.getId());
        assertThat(reload(debit).isTransfer()).isFalse();
        assertThat(reload(credit).isTransfer()).isFalse();
    }

    @Test
    void markingReversalPairedRowAsTransferReleasesPair() {
        Transaction debit = tx("DEBIT", "377.90", "ZOMATO", "2026-09-05");
        Transaction credit = tx("CREDIT", "377.90", "ZOMATO", "2026-09-06");
        reversalService.match(user.getId());

        transactionService.setTransfer(EMAIL, debit.getId(), new TransferRequest(true, null));

        assertThat(reload(debit).getReversalPairId()).isNull();
        assertThat(reload(credit).getReversalPairId()).isNull();
        assertThat(reload(debit).isTransfer()).isTrue();
    }
}
