package com.expenseanalyzer.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.repository.HiddenMerchantSuggestionRepository;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionReportRepository;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository;
import com.expenseanalyzer.merchant.repository.SharingStrikeRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import com.expenseanalyzer.merchant.service.MerchantService;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Account-level merchant-name sharing, per-name opt-out, and moderation (reports, hiding, strikes, revocation). */
@SpringBootTest
class MerchantNameSharingTest {

    private static final String ACME = "UPI/P2M/412345678902/ACME STORE/HDFC/";
    private static final String BETA = "UPI/P2M/412345678903/BETA MART/HDFC/";

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
    private MerchantSuggestionReportRepository reportRepository;
    @Autowired
    private HiddenMerchantSuggestionRepository hiddenRepository;
    @Autowired
    private SharingStrikeRepository strikeRepository;
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
        strikeRepository.deleteAll();
        hiddenRepository.deleteAll();
        reportRepository.deleteAll();
        voteRepository.deleteAll();
        aliasRepository.deleteAll();
        merchantRepository.deleteAll();
        users.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        users.clear();
    }

    /** A 30-day-old user (old enough for votes to count), with account-level sharing as given. */
    private User user(String email, boolean sharing) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        Timestamp created = Timestamp.from(Instant.now().minus(Duration.ofDays(30)));
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash, role, is_active, categories_seeded, created_at, "
                + "updated_at, share_merchant_names, sharing_revoked) VALUES (?, ?, 'x', 'USER', TRUE, FALSE, ?, ?, ?, FALSE)",
                u.getId(), email, created, created, sharing);
        users.add(u.getId());
        return u;
    }

    private String bearer(User u) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(u.getEmail(), null));
    }

    private UUID tx(User owner, String raw) {
        Transaction t = new Transaction();
        t.setRawDescription(raw);
        t.setNormalizedDescription(raw.replace('/', ' ').trim());
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(owner.getId());
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return transactionRepository.saveAndFlush(t).getId();
    }

    private void name(User u, UUID tx, String name, String shareJson) throws Exception {
        String share = shareJson == null ? "" : ",\"share\":" + shareJson;
        mockMvc.perform(put("/api/transactions/" + tx + "/merchant").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"" + share + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode names(User u) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/merchant-names").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode item(User u, String name) throws Exception {
        for (JsonNode i : names(u).get("items")) {
            if (i.get("name").asText().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("item not found");
    }

    private ResultActions setSharing(User u, boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/merchant-names/sharing").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"));
    }

    private ResultActions setAliasSharing(User u, String aliasId, boolean shared) throws Exception {
        return mockMvc.perform(put("/api/merchant-names/" + aliasId + "/sharing").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content("{\"shared\":" + shared + "}"));
    }

    private ResultActions report(User u, UUID tx) throws Exception {
        return mockMvc.perform(post("/api/transactions/" + tx + "/suggestion-report").header("Authorization", bearer(u)));
    }

    private String suggestedFor(User u, UUID tx) throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/transactions").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode row : list.get("items")) {
            if (row.get("id").asText().equals(tx.toString())) {
                return row.get("suggestedMerchantName").isNull() ? null : row.get("suggestedMerchantName").asText();
            }
        }
        throw new AssertionError("row not found");
    }

    private List<UUID> votersOf(User... us) {
        return voteRepository.findAll().stream().map(v -> v.getUserId())
                .filter(id -> List.of(us).stream().anyMatch(u -> u.getId().equals(id))).toList();
    }

    @Test
    void sharingIsOffByDefaultAndNamingWithShareRecordsNoVote() throws Exception {
        User a = user("a@example.com", false);
        name(a, tx(a, ACME + "x"), "Acme Store", "true");

        assertThat(voteRepository.findAll()).isEmpty();
        JsonNode body = names(a);
        assertThat(body.get("shareMerchantNames").asBoolean()).isFalse();
        assertThat(body.get("sharingRevoked").asBoolean()).isFalse();
        JsonNode acme = body.get("items").get(0);
        assertThat(acme.get("shared").asBoolean()).isFalse();
        assertThat(acme.get("shareable").asBoolean()).isTrue();
        assertThat(acme.get("transactionCount").asLong()).isEqualTo(1);
        assertThat(acme.get("payee").asText()).isEqualTo("ACME STORE");
    }

    @Test
    void switchingOnSharesOnlyEligibleNamesThatAreNotOptedOut() throws Exception {
        User a = user("a@example.com", false);
        name(a, tx(a, ACME + "x"), "Acme Store", null);
        tx(a, ACME + "y"); // second payment to the same payee
        name(a, tx(a, BETA + "x"), "Beta Mart", null);
        name(a, tx(a, "UPI/P2A/412345678904/RAHUL KUMAR/HDFC/x"), "Rahul Shop", null);
        name(a, tx(a, "NEFT RAHUL KUMAR"), "Neft Shop", null);
        name(a, tx(a, "UPI/P2M/412345678905/GAMMA/HDFC/"), "Gamma 24x7", null);
        setAliasSharing(a, item(a, "Beta Mart").get("id").asText(), false).andExpect(status().isOk());

        JsonNode body = objectMapper.readTree(setSharing(a, true).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.get("shareMerchantNames").asBoolean()).isTrue();
        assertThat(voteRepository.findAll()).singleElement().satisfies(v -> assertThat(v.getPayeeKey()).isEqualTo("ACME STORE"));
        assertThat(item(a, "Acme Store").get("shared").asBoolean()).isTrue();
        assertThat(item(a, "Acme Store").get("transactionCount").asLong()).isEqualTo(2);
        assertThat(item(a, "Beta Mart").get("optedOut").asBoolean()).isTrue();
        assertThat(item(a, "Beta Mart").get("shared").asBoolean()).isFalse();
        assertThat(item(a, "Rahul Shop").get("notShareableReason").asText()).isEqualTo("TRANSFER");
        assertThat(item(a, "Neft Shop").get("notShareableReason").asText()).isEqualTo("NOT_UPI_MERCHANT");
        assertThat(item(a, "Gamma 24x7").get("notShareableReason").asText()).isEqualTo("NAME_NOT_ALLOWED");
        assertThat(item(a, "Gamma 24x7").get("shareable").asBoolean()).isFalse();
        assertThat(item(a, "Acme Store").get("notShareableReason").isNull()).isTrue();
        List<String> order = new ArrayList<>();
        body.get("items").forEach(i -> order.add(i.get("name").asText()));
        assertThat(order).containsExactly("Acme Store", "Beta Mart", "Gamma 24x7", "Neft Shop", "Rahul Shop");
    }

    @Test
    void namingWhileOnSharesByDefaultAndShareFalseOptsOut() throws Exception {
        User a = user("a@example.com", true);
        name(a, tx(a, ACME + "x"), "Acme Store", null);
        name(a, tx(a, BETA + "x"), "Beta Mart", "false");

        assertThat(voteRepository.findAll()).singleElement().satisfies(v -> assertThat(v.getPayeeKey()).isEqualTo("ACME STORE"));
        assertThat(item(a, "Beta Mart").get("optedOut").asBoolean()).isTrue();
    }

    @Test
    void perNameOptOutDeletesTheVoteAndSurvivesTogglingTheAccountSetting() throws Exception {
        User a = user("a@example.com", true);
        name(a, tx(a, ACME + "x"), "Acme Store", null);
        String id = item(a, "Acme Store").get("id").asText();

        setAliasSharing(a, id, false).andExpect(status().isOk());
        assertThat(voteRepository.findAll()).isEmpty();
        setSharing(a, false).andExpect(status().isOk());
        setSharing(a, true).andExpect(status().isOk());
        assertThat(voteRepository.findAll()).isEmpty();

        String body = setAliasSharing(a, id, true).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("shared").asBoolean()).isTrue();
        assertThat(voteRepository.findAll()).hasSize(1);
    }

    @Test
    void perNameOptInWhileTheAccountIsOffRecordsNoVote() throws Exception {
        User a = user("a@example.com", false);
        name(a, tx(a, ACME + "x"), "Acme Store", null);

        setAliasSharing(a, item(a, "Acme Store").get("id").asText(), true).andExpect(status().isOk());

        assertThat(voteRepository.findAll()).isEmpty();
    }

    @Test
    void switchingOffDeletesAllTheUsersVotes() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        name(a, tx(a, ACME + "x"), "Acme Store", null);
        name(a, tx(a, BETA + "x"), "Beta Mart", null);
        name(b, tx(b, ACME + "y"), "Acme Store", null);
        assertThat(voteRepository.findAll()).hasSize(3);

        setSharing(a, false).andExpect(status().isOk());

        assertThat(voteRepository.findAll()).singleElement().satisfies(v -> assertThat(v.getUserId()).isEqualTo(b.getId()));
    }

    @Test
    void anotherUsersNameIs404AndListsHoldOnlyTheCallersNames() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        name(a, tx(a, ACME + "x"), "Acme Store", null);
        name(b, tx(b, BETA + "x"), "Beta Mart", null);
        String aliasOfA = item(a, "Acme Store").get("id").asText();

        setAliasSharing(b, aliasOfA, false).andExpect(status().isNotFound());
        setAliasSharing(b, UUID.randomUUID().toString(), false).andExpect(status().isNotFound());

        assertThat(voteRepository.findAll()).hasSize(2);
        JsonNode items = names(b).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("name").asText()).isEqualTo("Beta Mart");
        String raw = mockMvc.perform(get("/api/merchant-names").header("Authorization", bearer(b)))
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("ACME", "Acme", a.getId().toString(), "a@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Fuck Store", "Sh1t Mart", "B1TCH Shop", "Chutiya Dhaba", "Ch00tiya Store", "Fück Mart"})
    void blockedNamesAreNeverSharedButStillNameTheMerchant(String blocked) throws Exception {
        assertThat(MerchantService.offensive(blocked)).isTrue();
        User a = user("a@example.com", true);
        UUID aTx = tx(a, ACME + "x");
        name(a, aTx, blocked, "true");

        assertThat(voteRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findById(aTx).orElseThrow().getMerchantId()).isNotNull();
        JsonNode i = names(a).get("items").get(0);
        assertThat(i.get("shareable").asBoolean()).isFalse();
        assertThat(i.get("notShareableReason").asText()).isEqualTo("NAME_OFFENSIVE");
    }

    @Test
    void wordsThatOnlyContainABlockedWordAreAllowed() {
        assertThat(MerchantService.offensive("Scunthorpe Dickens Cocktail Bar")).isFalse();
        assertThat(MerchantService.nameReason("Acme Store")).isNull();
    }

    /** a and b share "Acme Store" for ACME; returns c's unmatched ACME row, which shows the suggestion. */
    private UUID shownSuggestion(User a, User b, User c) throws Exception {
        name(a, tx(a, ACME + "a"), "Acme Store", null);
        name(b, tx(b, ACME + "b"), "Acme Store", null);
        UUID row = tx(c, ACME + "c");
        assertThat(suggestedFor(c, row)).isEqualTo("Acme Store");
        return row;
    }

    @Test
    void aReportHidesTheSuggestionForTheReporterOnly() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        User c = user("c@example.com", false);
        User d = user("d@example.com", false);
        UUID cRow = shownSuggestion(a, b, c);
        UUID dRow = tx(d, ACME + "d");

        report(c, cRow).andExpect(status().isNoContent());

        assertThat(suggestedFor(c, cRow)).isNull();
        assertThat(suggestedFor(d, dRow)).isEqualTo("Acme Store");
        assertThat(hiddenRepository.findAll()).isEmpty();
        report(c, cRow).andExpect(status().isNotFound()); // no longer shown to c, so nothing to report
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    @Test
    void twoDistinctReportersHideItForEveryoneAndStrikeOnlyItsVoters() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        User other = user("other@example.com", true);
        User c = user("c@example.com", false);
        User d = user("d@example.com", false);
        User e = user("e@example.com", false);
        UUID cRow = shownSuggestion(a, b, c);
        name(other, tx(other, BETA + "o"), "Beta Mart", null);
        UUID dRow = tx(d, ACME + "d");
        UUID eRow = tx(e, ACME + "e");

        report(c, cRow).andExpect(status().isNoContent());
        report(d, dRow).andExpect(status().isNoContent());

        assertThat(suggestedFor(e, eRow)).isNull();
        assertThat(hiddenRepository.findAll()).hasSize(1);
        assertThat(strikeRepository.findAll()).extracting(s -> s.getUserId())
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(names(a).get("sharingRevoked").asBoolean()).isFalse(); // one strike is not enough
    }

    @Test
    void reportingAnotherUsersTransactionOrARowWithoutASuggestionIs404() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        User c = user("c@example.com", false);
        User d = user("d@example.com", false);
        UUID cRow = shownSuggestion(a, b, c);

        report(d, cRow).andExpect(status().isNotFound());
        report(c, tx(c, BETA + "c")).andExpect(status().isNotFound());
        report(c, UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(reportRepository.findAll()).isEmpty();
    }

    @Test
    void twoStrikesRevokeSharingWithoutTouchingTheAccount() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        User c = user("c@example.com", false);
        User d = user("d@example.com", false);
        UUID cAcme = shownSuggestion(a, b, c);
        name(a, tx(a, BETA + "a"), "Beta Mart", null);
        name(b, tx(b, BETA + "b"), "Beta Mart", null);
        UUID cBeta = tx(c, BETA + "c");
        UUID dAcme = tx(d, ACME + "d");
        UUID dBeta = tx(d, BETA + "d");
        name(a, tx(a, "UPI/P2M/412345678906/DELTA/HDFC/"), "Delta Shop", null);

        report(c, cAcme).andExpect(status().isNoContent());
        report(d, dAcme).andExpect(status().isNoContent());
        report(c, cBeta).andExpect(status().isNoContent());
        report(d, dBeta).andExpect(status().isNoContent());

        assertThat(votersOf(a, b)).isEmpty();
        JsonNode body = names(a);
        assertThat(body.get("sharingRevoked").asBoolean()).isTrue();
        assertThat(body.get("shareMerchantNames").asBoolean()).isFalse();
        setSharing(a, true).andExpect(status().isForbidden());
        setAliasSharing(a, item(a, "Delta Shop").get("id").asText(), true).andExpect(status().isForbidden());
        name(a, tx(a, "UPI/P2M/412345678907/EPSILON/HDFC/"), "Epsilon Shop", "true");
        assertThat(votersOf(a)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, a.getId())).isTrue();
    }

    @Test
    void aReportedOwnNameIsFlaggedWithoutCountsOrIdentities() throws Exception {
        User a = user("a@example.com", true);
        User b = user("b@example.com", true);
        User c = user("c@example.com", false);
        UUID cRow = shownSuggestion(a, b, c);
        report(c, cRow).andExpect(status().isNoContent());

        JsonNode acme = item(a, "Acme Store");
        assertThat(acme.get("reported").asBoolean()).isTrue();
        assertThat(acme.get("shared").asBoolean()).isTrue();
        String raw = mockMvc.perform(get("/api/merchant-names").header("Authorization", bearer(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain(c.getId().toString(), b.getId().toString(), "c@example.com", "b@example.com",
                "reporters", "voters");
        assertThat(acme.properties().stream().map(p -> p.getKey()).toList()).containsExactlyInAnyOrder("id", "name",
                "payee", "transactionCount", "shared", "optedOut", "shareable", "notShareableReason", "reported");
    }
}
