package com.expenseanalyzer.transaction.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TransactionControllerTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID alice;
    private UUID bob;
    private final UUID statement = UUID.randomUUID();
    private final UUID swiggy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        save(alice, "Coffee", "4.50", "2026-01-02", "DEBIT", statement, null);
        save(alice, "Swiggy order", "450.00", "2026-01-15", "DEBIT", statement, swiggy);
        save(alice, "Salary", "85000.00", "2026-01-31", "CREDIT", null, null);
        save(alice, "Rent", "20000.00", "2026-02-01", "DEBIT", null, null);
        save(bob, "Bob coffee", "4.50", "2026-01-02", "DEBIT", null, null);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
    }

    private UUID user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u.getId();
    }

    private Transaction save(UUID user, String desc, String rupees, String date, String type, UUID stmt, UUID merchant) {
        Transaction t = new Transaction(user, desc, new BigDecimal(rupees).movePointRight(2),
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant(), type);
        t.setStatementId(stmt);
        t.setMerchantId(merchant);
        t.setTransactionFingerprint(UUID.randomUUID().toString().replace("-", ""));
        return transactionRepository.save(t);
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyOwnTransactionsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/transactions").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].description").value("Rent"))
                .andExpect(jsonPath("$.items[3].description").value("Coffee"))
                .andExpect(jsonPath("$.items[3].amount").value(4.50))
                .andExpect(jsonPath("$.items[3].currency").value("INR"))
                .andExpect(jsonPath("$.items[3].date").value("2026-01-02"))
                .andExpect(jsonPath("$.items[3].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[3].transactionFingerprint").doesNotExist())
                .andExpect(jsonPath("$.items[3].userId").doesNotExist());
    }

    @Test
    void dateRangeIsInclusive() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "2026-01-02").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("Coffee", "Swiggy order", "Salary")));
    }

    @Test
    void filtersByStatementDirectionMerchantAndAmount() throws Exception {
        String token = bearer(ALICE);
        mockMvc.perform(get("/api/transactions").param("statementId", statement.toString()).header("Authorization", token))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(get("/api/transactions").param("direction", "CREDIT").header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("Salary")));
        mockMvc.perform(get("/api/transactions").param("merchantId", swiggy.toString()).header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("Swiggy order")));
        mockMvc.perform(get("/api/transactions").param("minAmount", "450").param("maxAmount", "20000")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.items[*].description", containsInAnyOrder("Swiggy order", "Rent")));
    }

    @Test
    void pagingUsesEnvelopeAndCapsSize() throws Exception {
        mockMvc.perform(get("/api/transactions").param("page", "1").param("size", "3").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/transactions").param("size", "100000").header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void invalidFilterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "2026-02-01").param("to", "2026-01-01")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/transactions").param("direction", "SIDEWAYS").header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOwnTransaction() throws Exception {
        Transaction t = transactionRepository.findByUserId(alice).getFirst();
        mockMvc.perform(get("/api/transactions/" + t.getId()).header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(t.getId().toString()));
    }

    @Test
    void otherUsersTransactionIsNotFound() throws Exception {
        Transaction bobs = transactionRepository.findByUserId(bob).getFirst();
        mockMvc.perform(get("/api/transactions/" + bobs.getId()).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNotFound());
    }
}
