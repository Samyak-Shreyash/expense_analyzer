package com.expenseanalyzer.statement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserAccountRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Renaming a statement, setting its account, and the user's own accounts: masking, ownership and transfers. */
@SpringBootTest
class StatementAccountsTest {

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
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private Statement statement;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        user(BOB);
        Statement s = new Statement();
        s.setUserId(alice.getId());
        s.setActive(true);
        s.setFileName("jan.csv");
        s.setDetectedAccount("6789");
        s.onCreate();
        statement = statementRepository.saveAndFlush(s);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private UUID tx(String description) {
        Transaction t = new Transaction();
        t.setRawDescription(description);
        t.setAmountPaise(new BigDecimal("500000"));
        t.setDate(JAN_10);
        t.setType("DEBIT");
        t.setUserId(alice.getId());
        t.setStatementId(statement.getId());
        t.setCreatedAt(JAN_10);
        t.setUpdatedAt(JAN_10);
        return transactionRepository.saveAndFlush(t).getId();
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private ResultActions patchStatement(String email, UUID id, String body) throws Exception {
        return mockMvc.perform(patch("/api/statements/" + id).header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions addAccount(String email, String body) throws Exception {
        return mockMvc.perform(post("/api/accounts").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void settingTheAccountRenamesMasksAddsItAndMarksTransfers() throws Exception {
        UUID toSelf = tx("NEFT TO XX6789 SELF");
        UUID shop = tx("UPI/P2M/663995116789/SHOP");

        patchStatement(ALICE, statement.getId(),
                "{\"fileName\":\" Salary account \",\"accountNumber\":\"5010-0123 456789\",\"bankName\":\" HDFC \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Salary account"))
                .andExpect(jsonPath("$.accountNumber").value("••6789"))
                .andExpect(jsonPath("$.bankName").value("HDFC"))
                .andExpect(jsonPath("$.detectedAccount").value("6789"))
                .andExpect(jsonPath("$.fileKept").value(false));

        assertThat(statementRepository.findById(statement.getId()).orElseThrow().getAccountNumber())
                .isEqualTo("50100123456789");
        assertThat(userAccountRepository.findByUserIdOrderByCreatedAt(alice.getId()))
                .singleElement().satisfies(a -> assertThat(a.getBankName()).isEqualTo("HDFC"));
        assertThat(transactionRepository.findById(toSelf).orElseThrow().isTransfer()).isTrue();
        assertThat(transactionRepository.findById(shop).orElseThrow().isTransfer()).isFalse();
    }

    @Test
    void aBlankAccountClearsItAndBlankBankClearsTheBank() throws Exception {
        patchStatement(ALICE, statement.getId(), "{\"accountNumber\":\"1234\",\"bankName\":\"AXIS\"}")
                .andExpect(status().isOk());

        patchStatement(ALICE, statement.getId(), "{\"accountNumber\":\"\",\"bankName\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.bankName").doesNotExist())
                .andExpect(jsonPath("$.fileName").value("jan.csv"));
    }

    @Test
    void rejectsBadNamesAndNumbersAndOtherUsersStatements() throws Exception {
        patchStatement(ALICE, statement.getId(), "{\"fileName\":\"  \"}").andExpect(status().isBadRequest());
        patchStatement(ALICE, statement.getId(), "{\"fileName\":\"" + "a".repeat(256) + "\"}")
                .andExpect(status().isBadRequest());
        patchStatement(ALICE, statement.getId(), "{\"bankName\":\"" + "a".repeat(256) + "\"}")
                .andExpect(status().isBadRequest());
        patchStatement(ALICE, statement.getId(), "{\"accountNumber\":\"12AB\"}").andExpect(status().isBadRequest());
        patchStatement(BOB, statement.getId(), "{\"fileName\":\"mine\"}").andExpect(status().isNotFound());

        assertThat(statementRepository.findById(statement.getId()).orElseThrow().getFileName()).isEqualTo("jan.csv");
    }

    @Test
    void ownAccountsAreAddedOnceListedMaskedAndDeletedByTheirOwnerOnly() throws Exception {
        UUID toSelf = tx("IMPS/50100123456789/SAVINGS");

        addAccount(ALICE, "{\"accountNumber\":\"50100123456789\",\"bankName\":\"\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("••6789"));
        addAccount(ALICE, "{\"accountNumber\":\"50100123456789\"}").andExpect(status().isCreated());
        addAccount(ALICE, "{\"accountNumber\":\"12\"}").andExpect(status().isBadRequest());
        addAccount(ALICE, "{\"accountNumber\":\"1234\",\"bankName\":\"" + "a".repeat(256) + "\"}")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/accounts").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("••6789"))
                .andExpect(jsonPath("$[0].bankName").doesNotExist());
        assertThat(transactionRepository.findById(toSelf).orElseThrow().isTransfer()).isTrue();

        UUID id = userAccountRepository.findByUserIdOrderByCreatedAt(alice.getId()).getFirst().getId();
        mockMvc.perform(delete("/api/accounts/" + id).header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/accounts/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());
        assertThat(userAccountRepository.count()).isZero();
    }
}
