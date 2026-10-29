package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private UserRepository userRepository;

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

    private void name(User u, UUID tx, String name, boolean suggest) throws Exception {
        mockMvc.perform(put("/api/transactions/" + tx + "/merchant").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"suggest\":" + suggest + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode list(User u) throws Exception {
        String body = mockMvc.perform(get("/api/transactions").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String suggestedFor(User u, UUID tx) throws Exception {
        for (JsonNode row : list(u).get("items")) {
            if (row.get("id").asText().equals(tx.toString())) {
                return row.get("suggestedMerchantName").isNull() ? null : row.get("suggestedMerchantName").asText();
            }
        }
        throw new AssertionError("row not found");
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
}
