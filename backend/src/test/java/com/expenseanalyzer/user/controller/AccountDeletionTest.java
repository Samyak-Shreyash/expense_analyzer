package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.auth.config.RateLimiter;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.processing.service.StatementProcessor;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AccountDeletionTest {

    private static final String ALICE = "alice-delete@example.com";
    private static final String BOB = "bob-delete@example.com";
    private static final String PASSWORD = "correct-password-1";
    /** Tables with a user_id column; users itself is checked by id. */
    private static final List<String> OWNED = List.of("outbox_events", "transactions", "statements", "person_payees",
            "people", "user_merchant_categories", "categories", "refresh_tokens", "user_merchant_aliases",
            "merchant_suggestion_votes", "merchant_suggestion_reports", "sharing_strikes", "preferences");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StatementProcessor processor;
    @Autowired
    private PreferenceRepository preferenceRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private S3StatementStorage storage;
    @MockitoBean
    private RateLimiter rateLimiter;

    private MockMvc mockMvc;
    private UUID alice;
    private UUID bob;
    private UUID merchant;
    private UUID aliceStatement;
    private UUID aliceAudit;
    private UUID bobAudit;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        merchant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO merchants (id, canonical_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                merchant, "Shared Merchant " + merchant, now, now);
        alice = seedUser(ALICE, now);
        bob = seedUser(BOB, now);
        aliceStatement = seedData(alice, "alice", now);
        seedData(bob, "bob", now);
        aliceAudit = seedAudit(alice, now);
        bobAudit = seedAudit(bob, now);
    }

    private UUID seedUser(String email, Timestamp now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, role, is_active, categories_seeded, "
                        + "share_merchant_names, sharing_revoked, created_at, updated_at) "
                        + "VALUES (?, ?, 'x', 'USER', TRUE, TRUE, FALSE, FALSE, ?, ?)", id, email, now, now);
        User user = new User(email, passwordEncoder.encode(PASSWORD));
        user.setId(id);
        user.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return id;
    }

    /** One row in every table the user owns; returns the statement id. */
    private UUID seedData(UUID user, String prefix, Timestamp now) {
        UUID statement = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        jdbc.update("INSERT INTO statements (id, user_id, status, format, storage_key, active, created_at, updated_at) "
                + "VALUES (?, ?, 'UPLOADED', 'CSV', ?, TRUE, ?, ?)", statement, user, "users/" + user + "/statements/x", now, now);
        jdbc.update("INSERT INTO categories (id, user_id, name, counts_as_spending, created_at, updated_at) "
                + "VALUES (?, ?, ?, TRUE, ?, ?)", category, user, prefix + "-cat", now, now);
        jdbc.update("INSERT INTO people (id, user_id, name, created_at) VALUES (?, ?, ?, ?)",
                person, user, prefix + "-person", now);
        jdbc.update("INSERT INTO person_payees (user_id, payee_key, person_id) VALUES (?, 'P', ?)",
                user, person);
        jdbc.update("INSERT INTO transactions (id, raw_description, amount_cents, date, type, user_id, statement_id, "
                        + "category_id, person_id, currency, transfer, transfer_manual, created_at, updated_at) "
                        + "VALUES (?, 'raw', 10.00, ?, 'DEBIT', ?, ?, ?, ?, 'INR', FALSE, FALSE, ?, ?)",
                tx, now, user, statement, category, person, now, now);
        jdbc.update("INSERT INTO user_merchant_categories (user_id, merchant_id, category_id) "
                + "VALUES (?, ?, ?)", user, merchant, category);
        jdbc.update("INSERT INTO user_merchant_aliases (public_id, user_id, payee_key, merchant_id, display_name, "
                        + "share_opt_out, created_at) VALUES (?, ?, 'K', ?, 'Mine', FALSE, ?)",
                UUID.randomUUID(), user, merchant, now);
        jdbc.update("INSERT INTO merchant_suggestion_votes (user_id, payee_key, merchant_id, created_at) "
                + "VALUES (?, 'K', ?, ?)", user, merchant, now);
        jdbc.update("INSERT INTO merchant_suggestion_reports (user_id, payee_key, merchant_id, reporter_aged, created_at) "
                + "VALUES (?, 'K', ?, FALSE, ?)", user, merchant, now);
        jdbc.update("INSERT INTO sharing_strikes (user_id, payee_key, merchant_id, created_at) "
                + "VALUES (?, 'K', ?, ?)", user, merchant, now);
        jdbc.update("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), user, UUID.randomUUID().toString(), now, now);
        jdbc.update("INSERT INTO outbox_events (id, event_type, aggregate_id, user_id, payload, created_at, attempt_count) "
                + "VALUES (?, 'StatementUploaded', ?, ?, '{}', ?, 0)", UUID.randomUUID(), statement, user, now);
        Preference preference = new Preference();
        preference.setUserId(user);
        preference.setKey("k");
        preferenceRepository.save(preference);
        return statement;
    }

    private UUID seedAudit(UUID user, Timestamp now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, user_id, action, occurred_at) VALUES (?, ?, 'AUTH_REGISTERED', ?)",
                id, user, now);
        return id;
    }

    @AfterEach
    void cleanUp() {
        for (UUID user : List.of(alice, bob)) {
            for (String table : OWNED) {
                jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", user);
            }
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", user);
            jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
        jdbc.update("DELETE FROM audit_logs WHERE id IN (?, ?)", aliceAudit, bobAudit);
        jdbc.update("DELETE FROM merchants WHERE id = ?", merchant);
    }

    private ResultActions deleteAccount(String email, String password) throws Exception {
        String token = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
        return mockMvc.perform(delete("/api/user/me").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"));
    }

    private int rows(String table, UUID user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, user);
    }

    private int userRows(UUID user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, user);
    }

    private void assertAllRowsPresent(UUID user) {
        assertThat(userRows(user)).isEqualTo(1);
        for (String table : OWNED) {
            assertThat(rows(table, user)).as(table).isEqualTo(1);
        }
    }

    @Test
    void removesEverythingTheUserOwnsAndNothingOfAnyoneElse() throws Exception {
        deleteAccount(ALICE, PASSWORD).andExpect(status().isNoContent());

        assertThat(userRows(alice)).isZero();
        for (String table : OWNED) {
            assertThat(rows(table, alice)).as(table).isZero();
        }
        verify(storage).delete("users/" + alice + "/statements/x");
        verify(storage, never()).delete("users/" + bob + "/statements/x");
        assertAllRowsPresent(bob);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchants WHERE id = ?", Integer.class, merchant)).isEqualTo(1);
    }

    @Test
    void auditRowsStayWithTheUserIdRemoved() throws Exception {
        deleteAccount(ALICE, PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT user_id FROM audit_logs WHERE id = ?", UUID.class, aliceAudit)).isNull();
        assertThat(jdbc.queryForObject("SELECT user_id FROM audit_logs WHERE id = ?", UUID.class, bobAudit)).isEqualTo(bob);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'ACCOUNT_DELETED' AND user_id IS NULL", Integer.class))
                .isPositive();
    }

    @Test
    void wrongPasswordChangesNothing() throws Exception {
        deleteAccount(ALICE, "wrong-password").andExpect(status().isBadRequest());

        assertAllRowsPresent(alice);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void attemptsAreRateLimited() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(rateLimiter).check(anyString(), any());

        deleteAccount(ALICE, PASSWORD).andExpect(status().isTooManyRequests());

        assertAllRowsPresent(alice);
    }

    @Test
    void missingPasswordIsRejected() throws Exception {
        deleteAccount(ALICE, "").andExpect(status().isBadRequest());

        assertAllRowsPresent(alice);
    }

    @Test
    void storageFailureLeavesRowsAndCanBeRetried() throws Exception {
        doThrow(SdkClientException.create("down")).when(storage).delete(anyString());
        deleteAccount(ALICE, PASSWORD).andExpect(status().isServiceUnavailable());
        assertAllRowsPresent(alice);

        doNothing().when(storage).delete(anyString());
        deleteAccount(ALICE, PASSWORD).andExpect(status().isNoContent());
        assertThat(userRows(alice)).isZero();
    }

    @Test
    void eventAlreadyInKafkaDoesNotBringDataBack() throws Exception {
        deleteAccount(ALICE, PASSWORD).andExpect(status().isNoContent());

        processor.process("""
                {"eventId":"%s","eventType":"StatementUploaded","eventVersion":1,"occurredAt":"2026-01-01T00:00:00Z",
                 "userId":"%s","aggregateId":"%s","payload":{"statementId":"%s","storageKey":"k"}}
                """.formatted(UUID.randomUUID(), alice, aliceStatement, aliceStatement));

        assertThat(rows("transactions", alice)).isZero();
        assertThat(rows("statements", alice)).isZero();
    }

    @Test
    void withoutATokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/user/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        assertAllRowsPresent(alice);
    }
}
