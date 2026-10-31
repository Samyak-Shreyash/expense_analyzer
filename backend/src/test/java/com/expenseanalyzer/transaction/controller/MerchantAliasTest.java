package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonPayeeRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.processing.service.StatementProcessor;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

/** Naming the merchant of an unmatched payment: scope, import, validation and per-user isolation. */
@SpringBootTest
class MerchantAliasTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String ACME = "UPI/P2M/412345678902/ACME STORE/HDFC/";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private PersonPayeeRepository personPayeeRepository;
    @Autowired
    private UserMerchantAliasRepository aliasRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private StatementProcessor processor;

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserMerchantCategoryRepository merchantCategoryRepository;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private StatementStorage storage;

    private MockMvc mockMvc;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        merchantCategoryRepository.deleteAll();
        categoryRepository.deleteAll();
        statementRepository.deleteAll();
        processedEventRepository.deleteAll();
        aliasRepository.deleteAll();
        personPayeeRepository.deleteAll();
        personRepository.deleteAll();
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

    private UUID transaction(User owner, String raw, String normalized) {
        Transaction t = new Transaction();
        t.setRawDescription(raw);
        t.setNormalizedDescription(normalized);
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(owner.getId());
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return transactionRepository.saveAndFlush(t).getId();
    }

    private void name(String email, UUID tx, String body, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + tx + "/merchant").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    private Transaction reload(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    @Test
    void namingAnUnmatchedPaymentAppliesToSamePayeeOnly() throws Exception {
        UUID a = transaction(alice, ACME + "lunch", "P2M ACME STORE LUNCH");
        UUID b = transaction(alice, ACME + "dinner", "P2M ACME STORE DINNER");
        UUID other = transaction(alice, "UPI/P2M/412345678903/OTHER SHOP/HDFC/x", "P2M OTHER SHOP");
        UUID bobs = transaction(bob, ACME + "lunch", "P2M ACME STORE LUNCH");
        Person rahul = personRepository.saveAndFlush(new Person(alice.getId(), "Rahul"));
        Transaction withPerson = reload(b);
        withPerson.setPersonId(rahul.getId());
        transactionRepository.saveAndFlush(withPerson);

        mockMvc.perform(put("/api/transactions/" + a + "/merchant").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Acme   Store \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchantName").value("Acme Store"));

        UUID merchant = merchantRepository.findByCanonicalName("Acme Store").orElseThrow().getId();
        assertThat(reload(a).getMerchantId()).isEqualTo(merchant);
        assertThat(reload(b).getMerchantId()).isEqualTo(merchant);
        assertThat(reload(b).getPersonId()).isNull();
        assertThat(reload(other).getMerchantId()).isNull();
        assertThat(reload(bobs).getMerchantId()).isNull();
        assertThat(reload(a).getRawDescription()).isEqualTo(ACME + "lunch");
        assertThat(aliasRepository.findByUserId(alice.getId())).hasSize(1);
        assertThat(aliasRepository.findByUserId(bob.getId())).isEmpty();
    }

    @Test
    void namingMovesTheRowsOwnCategoryToTheMerchantUnlessItHasOne() throws Exception {
        UUID food = categoryRepository.saveAndFlush(new Category(alice.getId(), "Food", true)).getId();
        UUID shops = categoryRepository.saveAndFlush(new Category(alice.getId(), "Shops", true)).getId();
        UUID a = transaction(alice, ACME + "lunch", "P2M ACME STORE LUNCH");
        UUID b = transaction(alice, ACME + "dinner", "P2M ACME STORE DINNER");
        Transaction row = reload(a);
        row.setCategoryId(food);
        transactionRepository.saveAndFlush(row);

        name(ALICE, a, "{\"name\":\"Acme Store\"}", 200);

        UUID merchant = merchantRepository.findByCanonicalName("Acme Store").orElseThrow().getId();
        assertThat(merchantCategoryRepository.findByUserIdAndMerchantId(alice.getId(), merchant).orElseThrow()
                .getCategoryId()).isEqualTo(food);
        assertThat(reload(a).getCategoryId()).isNull();
        assertThat(reload(b).getCategoryId()).isNull();

        // The merchant already has a category: the row keeps its own.
        Transaction other = reload(b);
        other.setCategoryId(shops);
        transactionRepository.saveAndFlush(other);
        name(ALICE, b, "{\"name\":\"Acme Store\"}", 200);
        assertThat(merchantCategoryRepository.findByUserIdAndMerchantId(alice.getId(), merchant).orElseThrow()
                .getCategoryId()).isEqualTo(food);
        assertThat(reload(b).getCategoryId()).isEqualTo(shops);
    }

    @Test
    void laterImportOfSamePayeeGetsTheMerchantAutomatically() throws Exception {
        UUID a = transaction(alice, ACME + "lunch", "P2M ACME STORE LUNCH");
        name(ALICE, a, "{\"name\":\"Acme Store\"}", 200);
        UUID merchant = reload(a).getMerchantId();

        Statement s = statement(alice.getId(), "Date,Description,Amount\n2026-02-02," + ACME + "snack,-5.00\n"
                + "2026-02-03,UPI/P2M/412345678903/OTHER SHOP/HDFC/x,-6.00\n");
        processor.process(event(s));
        Statement bobsStatement = statement(bob.getId(), "Date,Description,Amount\n2026-02-02," + ACME + "snack,-5.00\n");
        processor.process(event(bobsStatement));

        var mine = transactionRepository.findByUserIdAndStatementId(alice.getId(), s.getId());
        assertThat(mine).hasSize(2);
        for (Transaction t : mine) {
            assertThat(t.getMerchantId()).isEqualTo(t.getRawDescription().contains("ACME") ? merchant : null);
        }
        assertThat(transactionRepository.findByUserIdAndStatementId(bob.getId(), bobsStatement.getId()))
                .hasSize(1).allSatisfy(t -> assertThat(t.getMerchantId()).isNull());
    }

    @Test
    void counterpartyMerchantCorrectionConsultsTheAlias() throws Exception {
        UUID a = transaction(alice, ACME + "lunch", "P2M ACME STORE LUNCH");
        name(ALICE, a, "{\"name\":\"Acme Store\"}", 200);
        UUID merchant = reload(a).getMerchantId();
        Transaction t = reload(a);
        t.setMerchantId(null);
        transactionRepository.saveAndFlush(t);

        mockMvc.perform(put("/api/transactions/" + a + "/counterparty").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MERCHANT\"}"))
                .andExpect(status().isOk());

        assertThat(reload(a).getMerchantId()).isEqualTo(merchant);
    }

    @Test
    void invalidNamesAndForeignTransactionsAreRejected() throws Exception {
        UUID mine = transaction(alice, ACME + "lunch", "P2M ACME STORE LUNCH");
        UUID bobsTx = transaction(bob, ACME + "lunch", "P2M ACME STORE LUNCH");

        name(ALICE, mine, "{\"name\":\"   \"}", 400);
        name(ALICE, mine, "{}", 400);
        name(ALICE, mine, "{\"name\":\"" + "x".repeat(101) + "\"}", 400);
        name(ALICE, bobsTx, "{\"name\":\"Acme\"}", 404);
        name(ALICE, UUID.randomUUID(), "{\"name\":\"Acme\"}", 404);

        assertThat(reload(mine).getMerchantId()).isNull();
        assertThat(reload(bobsTx).getMerchantId()).isNull();
        assertThat(aliasRepository.findAll()).isEmpty();
    }

    private Statement statement(UUID userId, String csv) {
        Statement s = new Statement();
        s.setUserId(userId);
        s.setActive(true);
        s.setFormat(StatementFormat.CSV);
        s.setStatus(StatementStatus.QUEUED);
        s.setFileName("statement.csv");
        s.setFileHash(UUID.randomUUID().toString().replace("-", ""));
        s.onCreate();
        s = statementRepository.saveAndFlush(s);
        s.setStorageKey(StatementStorage.keyFor(userId, s.getId()));
        s = statementRepository.saveAndFlush(s);
        when(storage.read(s.getStorageKey()))
                .thenAnswer(inv -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        return s;
    }

    private static String event(Statement s) {
        return """
                {"eventId":"%s","eventType":"StatementUploaded","eventVersion":1,"occurredAt":"2026-01-01T00:00:00Z",
                 "producer":"statement-module","correlationId":"corr-1","userId":"%s","aggregateId":"%s",
                 "payload":{"statementId":"%s","storageKey":"%s","format":"CSV","fileHash":"x"}}
                """.formatted(UUID.randomUUID(), s.getUserId(), s.getId(), s.getId(), s.getStorageKey());
    }
}
