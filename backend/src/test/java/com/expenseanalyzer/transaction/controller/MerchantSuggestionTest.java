package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Opt-in shared merchant suggestions: the k >= 2 threshold, withdrawal, and what is never shared. */
@SpringBootTest
class MerchantSuggestionTest {

    private static final String ACME = "UPI/P2M/412345678902/ACME STORE/HDFC/";
    private static final String P2A = "UPI/P2A/412345678902/RAHUL KUMAR/HDFC/x";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private UserMerchantAliasRepository aliasRepository;
    @Autowired
    private MerchantSuggestionVoteRepository voteRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserRepository userRepository;

    private final List<UUID> users = new ArrayList<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        voteRepository.deleteAll();
        aliasRepository.deleteAll();
        merchantRepository.deleteAll();
        users.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        users.clear();
    }

    /** A user whose account is old enough for their votes to count. */
    private User user(String email) {
        return user(email, Duration.ofDays(30));
    }

    /** A user (sharing merchant names) stored with the given account age (votes count only from accounts at least 7 days old). */
    private User user(String email, Duration age) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        Timestamp created = Timestamp.from(Instant.now().minus(age));
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash, role, is_active, categories_seeded, "
                + "created_at, updated_at, share_merchant_names) VALUES (?, ?, 'x', 'USER', TRUE, FALSE, ?, ?, TRUE)", u.getId(), email, created, created);
        users.add(u.getId());
        return u;
    }

    private String bearer(User u) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(u.getEmail(), null));
    }

    private UUID tx(User owner, String raw, String normalized) {
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

    private void name(User u, UUID tx, String name, boolean share) throws Exception {
        mockMvc.perform(put("/api/transactions/" + tx + "/merchant").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"share\":" + share + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode rowFor(User u, UUID tx) throws Exception {
        for (JsonNode row : list(u).get("items")) {
            if (row.get("id").asText().equals(tx.toString())) {
                return row;
            }
        }
        throw new AssertionError("row not found");
    }

    private JsonNode list(User u) throws Exception {
        String body = mockMvc.perform(get("/api/transactions").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String suggestedFor(User u, UUID tx) throws Exception {
        JsonNode suggested = rowFor(u, tx).get("suggestedMerchantName");
        return suggested.isNull() ? null : suggested.asText();
    }

    /** Two users suggest "Acme Store" for the Acme payee. */
    private void twoVoters(User a, User b) throws Exception {
        name(a, tx(a, ACME + "x", "P2M ACME STORE X"), "Acme Store", true);
        name(b, tx(b, ACME + "y", "P2M ACME STORE Y"), "Acme Store", true);
    }

    @Test
    void oneVoterIsNotEnough() throws Exception {
        User a = user("a@example.com");
        User c = user("c@example.com");
        name(a, tx(a, ACME + "x", "P2M ACME STORE X"), "Acme Store", true);
        UUID mine = tx(c, ACME + "z", "P2M ACME STORE Z");

        assertThat(suggestedFor(c, mine)).isNull();
    }

    @Test
    void twoVotersSuggestToAThirdUserOnlyOnUnmatchedRows() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        User c = user("c@example.com");
        twoVoters(a, b);
        UUID unmatched = tx(c, ACME + "z", "P2M ACME STORE Z");
        UUID matched = tx(c, ACME + "w", "P2M ACME STORE W");
        Transaction m = transactionRepository.findById(matched).orElseThrow();
        m.setMerchantId(merchantRepository.findByCanonicalName("Acme Store").orElseThrow().getId());
        transactionRepository.saveAndFlush(m);
        UUID unrelated = tx(c, "UPI/P2M/412345678903/OTHER SHOP/HDFC/x", "P2M OTHER SHOP");

        assertThat(suggestedFor(c, unmatched)).isEqualTo("Acme Store");
        assertThat(suggestedFor(c, matched)).isNull();
        assertThat(suggestedFor(c, unrelated)).isNull();
    }

    @Test
    void acceptingNamesTheMerchantWithoutVoting() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        User c = user("c@example.com");
        twoVoters(a, b);
        UUID mine = tx(c, ACME + "z", "P2M ACME STORE Z");

        name(c, mine, "Acme Store", false);

        assertThat(transactionRepository.findById(mine).orElseThrow().getMerchantId()).isNotNull();
        assertThat(voteRepository.findAll()).hasSize(2);
        assertThat(aliasRepository.findByUserId(c.getId())).hasSize(1);
    }

    @Test
    void transfersToPeopleAreNeverRecorded() throws Exception {
        User a = user("a@example.com");
        name(a, tx(a, P2A, "P2A RAHUL KUMAR"), "Rahul Store", true);

        assertThat(voteRepository.findAll()).isEmpty();
        assertThat(aliasRepository.findByUserId(a.getId())).hasSize(1);
    }

    @Test
    void withdrawingDropsTheSuggestionBelowTwo() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        User c = user("c@example.com");
        UUID aTx = tx(a, ACME + "x", "P2M ACME STORE X");
        name(a, aTx, "Acme Store", true);
        name(b, tx(b, ACME + "y", "P2M ACME STORE Y"), "Acme Store", true);
        UUID mine = tx(c, ACME + "z", "P2M ACME STORE Z");
        assertThat(suggestedFor(c, mine)).isEqualTo("Acme Store");

        name(a, aTx, "Acme Store", false);

        assertThat(voteRepository.findAll()).hasSize(1);
        assertThat(suggestedFor(c, mine)).isNull();
    }

    @Test
    void mostVotedMerchantWins() throws Exception {
        User[] voters = new User[5];
        for (int i = 0; i < voters.length; i++) {
            voters[i] = user("v" + i + "@example.com");
            name(voters[i], tx(voters[i], ACME + i, "P2M ACME STORE " + i), i < 2 ? "Acme Store" : "Acme Shop", true);
        }
        User f = user("f@example.com");
        UUID mine = tx(f, ACME + "6", "P2M ACME STORE 6");

        assertThat(suggestedFor(f, mine)).isEqualTo("Acme Shop");
    }

    @Test
    void callersOwnVoteNeverCountsTowardTheirSuggestion() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        name(a, tx(a, ACME + "1", "P2M ACME STORE 1"), "Acme Store", true);
        name(b, tx(b, ACME + "2", "P2M ACME STORE 2"), "Acme Store", true);
        UUID aUnmatched = tx(a, ACME + "3", "P2M ACME STORE 3"); // new unmatched row after a already voted

        assertThat(suggestedFor(a, aUnmatched)).isNull();
    }

    @Test
    void responseNeverContainsVoterInformation() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        User c = user("c@example.com");
        twoVoters(a, b);
        tx(c, ACME + "z", "P2M ACME STORE Z");

        String body = mockMvc.perform(get("/api/transactions").header("Authorization", bearer(c)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(a.getId().toString(), b.getId().toString(), a.getEmail(), b.getEmail())
                .doesNotContainIgnoringCase("voter").doesNotContainIgnoringCase("votes");
        assertThat(list(c).get("items")).hasSize(1); // only the caller's own rows
    }

    @Test
    void namesDifferingOnlyInCaseShareOneMerchant() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        name(a, tx(a, ACME + "x", "P2M ACME STORE X"), "Acme Store", true);
        name(b, tx(b, ACME + "y", "P2M ACME STORE Y"), "acme store", true);

        assertThat(merchantRepository.findAll()).hasSize(1)
                .allSatisfy(m -> assertThat(m.getCanonicalName()).isEqualTo("Acme Store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPI/P2A/412345678902/9876543210/HDFC/x",
            "UPI/P2A/412345678902/rahul@okhdfc/HDFC/x",
            "NEFT/N123456789012/RAHUL KUMAR/HDFC",
            "UPI/P2M/412345678902/ACME STORE/P2A/x"})
    void nonMerchantPayeesAreNeverRecorded(String raw) throws Exception {
        User a = user("a@example.com");
        name(a, tx(a, raw, "NORMALIZED " + raw.length()), "Rahul Store", true);

        assertThat(voteRepository.findAll()).isEmpty();
        assertThat(aliasRepository.findByUserId(a.getId())).hasSize(1);
    }

    @Test
    void merchantPaymentsStillRecordAndReplaceTheVote() throws Exception {
        User a = user("a@example.com");
        UUID aTx = tx(a, ACME + "x", "P2M ACME STORE X");
        name(a, aTx, "Acme Store", true);
        name(a, aTx, "Acme Shop", true);

        assertThat(voteRepository.findAll()).singleElement().satisfies(v -> assertThat(v.getMerchantId())
                .isEqualTo(merchantRepository.findByCanonicalName("Acme Shop").orElseThrow().getId()));
    }

    @Test
    void aRowWithATransferTokenNeverShowsASuggestion() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        User c = user("c@example.com");
        twoVoters(a, b);
        UUID transfer = tx(c, ACME + "P2A/z", "P2M ACME STORE P2A Z");

        assertThat(suggestedFor(c, transfer)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Acme 24x7", "www.acme.com", "acme@pay", "Acme/Store", "A", "Call: 98765"})
    void unsafeNamesAreNotRecordedButStillNameTheMerchant(String unsafe) throws Exception {
        User a = user("a@example.com");
        UUID aTx = tx(a, ACME + "x", "P2M ACME STORE X");
        name(a, aTx, unsafe, true);

        assertThat(voteRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findById(aTx).orElseThrow().getMerchantId()).isNotNull();
    }

    @Test
    void safeNamesInAnyScriptAreRecorded() throws Exception {
        User a = user("a@example.com");
        name(a, tx(a, ACME + "x", "P2M ACME STORE X"), "Dr. O'Brien & Sons - काफ़ी", true);

        assertThat(voteRepository.findAll()).hasSize(1);
    }

    @Test
    void aTieShowsNothing() throws Exception {
        User[] voters = new User[4];
        for (int i = 0; i < voters.length; i++) {
            voters[i] = user("v" + i + "@example.com");
            name(voters[i], tx(voters[i], ACME + i, "P2M ACME STORE " + i), i < 2 ? "Acme Store" : "Acme Shop", true);
        }
        User f = user("f@example.com");
        UUID mine = tx(f, ACME + "6", "P2M ACME STORE 6");

        assertThat(suggestedFor(f, mine)).isNull();
    }

    @Test
    void votesFromAccountsYoungerThanSevenDaysDoNotCount() throws Exception {
        User a = user("a@example.com");
        User young = user("young@example.com", Duration.ofDays(6));
        User c = user("c@example.com");
        twoVoters(a, young);
        UUID mine = tx(c, ACME + "z", "P2M ACME STORE Z");

        assertThat(suggestedFor(c, mine)).isNull();
    }

    @Test
    void eachUserSeesTheirOwnSpelling() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        UUID aTx = tx(a, ACME + "x", "P2M ACME STORE X");
        UUID bTx = tx(b, ACME + "y", "P2M ACME STORE Y");
        name(a, aTx, "Acme Store", false);

        mockMvc.perform(put("/api/transactions/" + bTx + "/merchant").header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"acme STORE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchantName").value("acme STORE"));

        assertThat(rowFor(b, bTx).get("merchantName").asText()).isEqualTo("acme STORE");
        assertThat(rowFor(a, aTx).get("merchantName").asText()).isEqualTo("Acme Store");
        assertThat(merchantRepository.findAll()).hasSize(1);
    }

    @Test
    void markingThePayeeAsAPersonDropsTheAliasAndVote() throws Exception {
        User a = user("a@example.com");
        UUID aTx = tx(a, ACME + "x", "P2M ACME STORE X");
        name(a, aTx, "Acme Store", true);
        assertThat(voteRepository.findAll()).hasSize(1);

        mockMvc.perform(put("/api/transactions/" + aTx + "/counterparty").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PERSON\",\"name\":\"Rahul\"}"))
                .andExpect(status().isOk());

        assertThat(voteRepository.findAll()).isEmpty();
        assertThat(aliasRepository.findByUserId(a.getId())).isEmpty();
    }
}
