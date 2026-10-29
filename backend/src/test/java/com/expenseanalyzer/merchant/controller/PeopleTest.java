package com.expenseanalyzer.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

/** People, counterparty corrections and the person step of the effective category. */
@SpringBootTest
class PeopleTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String SWIGGY_RAW = "UPI-SWIGGY-ORDER-12345678";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserMerchantCategoryRepository mappingRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private PersonRepository personRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private UUID swiggy;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        swiggy = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        personRepository.deleteAll();
        mappingRepository.deleteAll();
        categoryRepository.deleteAll();
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

    private String createCategory(String email, String name) throws Exception {
        String json = mockMvc.perform(post("/api/categories").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"countsAsSpending\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private UUID transaction(User owner, String raw, String normalized, UUID merchantId, UUID personId) {
        Transaction t = new Transaction();
        t.setRawDescription(raw);
        t.setNormalizedDescription(normalized);
        t.setAmountCents(new BigDecimal("10.00"));
        t.setDate(Instant.now());
        t.setType("DEBIT");
        t.setUserId(owner.getId());
        t.setMerchantId(merchantId);
        t.setPersonId(personId);
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return transactionRepository.saveAndFlush(t).getId();
    }

    private Person person(User owner, String name, String categoryId) {
        Person p = new Person(owner.getId(), name);
        p.setCategoryId(categoryId == null ? null : UUID.fromString(categoryId));
        return personRepository.saveAndFlush(p);
    }

    private void counterparty(String email, UUID tx, String body, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + tx + "/counterparty").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    @Test
    void personCorrectionAppliesToSameNormalizedDescriptionAndBackToMerchant() throws Exception {
        UUID a = transaction(alice, SWIGGY_RAW, "SWIGGY ORDER", swiggy, null);
        UUID b = transaction(alice, "POS 4521XXXX1234 SWIGGY ORDER", "SWIGGY ORDER", swiggy, null);
        UUID other = transaction(alice, "UPI-SWIGGY-LATE-99", "SWIGGY LATE", swiggy, null);
        UUID bobs = transaction(bob, SWIGGY_RAW, "SWIGGY ORDER", swiggy, null);

        counterparty(ALICE, a, "{\"type\":\"PERSON\",\"name\":\"  Rahul   Sharma \"}", 200);

        for (UUID id : new UUID[] {a, b}) {
            mockMvc.perform(get("/api/transactions/" + id).header("Authorization", bearer(ALICE)))
                    .andExpect(jsonPath("$.personName").value("Rahul Sharma"))
                    .andExpect(jsonPath("$.merchantId").doesNotExist());
        }
        mockMvc.perform(get("/api/transactions/" + other).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.merchantId").value(swiggy.toString()))
                .andExpect(jsonPath("$.personId").doesNotExist());
        assertThat(transactionRepository.findById(bobs).orElseThrow().getPersonId()).isNull();
        assertThat(personRepository.findByUserIdOrderByNameAsc(alice.getId())).hasSize(1);
        Transaction raw = transactionRepository.findById(a).orElseThrow();
        assertThat(raw.getRawDescription()).isEqualTo(SWIGGY_RAW);
        assertThat(raw.getAmountCents()).isEqualByComparingTo("10.00");

        counterparty(ALICE, b, "{\"type\":\"MERCHANT\"}", 200);

        for (UUID id : new UUID[] {a, b}) {
            mockMvc.perform(get("/api/transactions/" + id).header("Authorization", bearer(ALICE)))
                    .andExpect(jsonPath("$.personId").doesNotExist())
                    .andExpect(jsonPath("$.merchantId").value(swiggy.toString()));
        }
    }

    @Test
    void switchingToMerchantWithNoMatchingRuleLeavesNoMerchant() throws Exception {
        Person rahul = person(alice, "Rahul Sharma", null);
        UUID tx = transaction(alice, "UPI/P2A/412345678901/RAHUL SHARMA/HDFC BANK/rent", "P2A RAHUL SHARMA", null,
                rahul.getId());

        counterparty(ALICE, tx, "{\"type\":\"MERCHANT\"}", 200);

        Transaction t = transactionRepository.findById(tx).orElseThrow();
        assertThat(t.getPersonId()).isNull();
        assertThat(t.getMerchantId()).isNull();
        assertThat(t.getRawDescription()).isEqualTo("UPI/P2A/412345678901/RAHUL SHARMA/HDFC BANK/rent");
    }

    @Test
    void invalidCounterpartyRequestsAndOwnershipAreRejected() throws Exception {
        UUID mine = transaction(alice, SWIGGY_RAW, "SWIGGY ORDER", swiggy, null);
        UUID bobsTx = transaction(bob, SWIGGY_RAW, "SWIGGY ORDER", swiggy, null);
        Person bobsPerson = person(bob, "Secret Friend", null);
        String pets = createCategory(ALICE, "Pets");
        String bobsCategory = createCategory(BOB, "Secret");

        counterparty(ALICE, bobsTx, "{\"type\":\"PERSON\",\"name\":\"X\"}", 404);
        counterparty(ALICE, UUID.randomUUID(), "{\"type\":\"MERCHANT\"}", 404);
        counterparty(ALICE, mine, "{\"type\":\"PERSON\",\"name\":\"  \"}", 400);
        counterparty(ALICE, mine, "{\"type\":\"PERSON\"}", 400);
        counterparty(ALICE, mine, "{}", 400);
        mockMvc.perform(put("/api/people/" + bobsPerson.getId()).header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hax\",\"categoryId\":\"" + pets + "\"}"))
                .andExpect(status().isNotFound());

        Person own = person(alice, "Rahul Sharma", null);
        mockMvc.perform(put("/api/people/" + own.getId()).header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rahul\",\"categoryId\":\"" + bobsCategory + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/people").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Rahul Sharma")));
        assertThat(personRepository.findById(bobsPerson.getId()).orElseThrow().getName()).isEqualTo("Secret Friend");
    }

    @Test
    void personCategoryDrivesEffectiveCategoryAndFilterWithOwnCategoryWinning() throws Exception {
        String pets = createCategory(ALICE, "Pets");
        String gifts = createCategory(ALICE, "Gifts");
        Person rahul = person(alice, "Rahul Sharma", null);
        UUID viaPerson = transaction(alice, "p1", "P1", null, rahul.getId());
        UUID overridden = transaction(alice, "p2", "P2", null, rahul.getId());
        transaction(alice, "plain", "PLAIN", null, null);
        mockMvc.perform(put("/api/transactions/" + overridden + "/category").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":\"" + gifts + "\"}"));
        String token = bearer(ALICE);

        mockMvc.perform(get("/api/transactions/" + viaPerson).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Uncategorized"));

        mockMvc.perform(put("/api/people/" + rahul.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rahul Sharma\",\"categoryId\":\"" + pets + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(pets));

        mockMvc.perform(get("/api/transactions/" + viaPerson).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Pets")).andExpect(jsonPath("$.categoryOverridden").value(false))
                .andExpect(jsonPath("$.personName").value("Rahul Sharma"));
        mockMvc.perform(get("/api/transactions/" + overridden).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Gifts"));
        mockMvc.perform(get("/api/transactions").param("category", "Pets").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("p1")));
        mockMvc.perform(get("/api/transactions").param("category", "Gifts").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("p2")));
        mockMvc.perform(get("/api/transactions").param("category", "Uncategorized").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("plain")));

        mockMvc.perform(delete("/api/categories/" + pets).header("Authorization", token)).andExpect(status().isNoContent());
        assertThat(personRepository.findById(rahul.getId()).orElseThrow().getCategoryId()).isNull();
        mockMvc.perform(get("/api/transactions/" + viaPerson).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Uncategorized"));
    }

    @Test
    void danglingTransactionCategoryIsTreatedAsNotSetByResolverAndFilter() throws Exception {
        String pets = createCategory(ALICE, "Pets");
        Person rahul = person(alice, "Rahul Sharma", pets);
        UUID dangling = transaction(alice, "dangling-person", "D1", null, rahul.getId());
        UUID danglingNone = transaction(alice, "dangling-none", "D2", null, null);
        for (UUID id : new UUID[] {dangling, danglingNone}) {
            Transaction t = transactionRepository.findById(id).orElseThrow();
            t.setCategoryId(UUID.randomUUID()); // not one of the user's categories
            transactionRepository.saveAndFlush(t);
        }
        String token = bearer(ALICE);

        mockMvc.perform(get("/api/transactions/" + dangling).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Pets"));
        mockMvc.perform(get("/api/transactions/" + danglingNone).header("Authorization", token))
                .andExpect(jsonPath("$.category").value("Uncategorized"));
        mockMvc.perform(get("/api/transactions").param("category", "Pets").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("dangling-person")));
        mockMvc.perform(get("/api/transactions").param("category", "Uncategorized").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("dangling-none")));
    }
}
