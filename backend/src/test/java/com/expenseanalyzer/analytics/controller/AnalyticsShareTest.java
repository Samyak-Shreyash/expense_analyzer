package com.expenseanalyzer.analytics.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Merchant and category breakdowns share one spending total; month-over-month change is per covered day. */
@SpringBootTest
class AnalyticsShareTest {

    private static final String ALICE = "alice-share@example.com";

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
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID alice;
    private final List<UUID> merchantIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(userRepository.claimCategorySeeding(org.mockito.ArgumentMatchers.any())).thenReturn(1); // mocked repo: always claim
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(ALICE);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(ALICE)).thenReturn(Optional.of(u));
        alice = u.getId();
    }

    @AfterEach
    void cleanUp() {
        for (String table : List.of("transactions", "people", "categories")) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", alice); // only this test's user
        }
        merchantRepository.deleteAllById(merchantIds);
        merchantIds.clear();
    }

    private void save(String date, String rupees, UUID merchant, UUID person) {
        Transaction t = new Transaction(alice, "tx", new BigDecimal(rupees).movePointRight(2),
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant(), "DEBIT");
        t.setMerchantId(merchant);
        t.setPersonId(person);
        transactionRepository.save(t);
    }

    private ResultActions call(String path, String from, String to) throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        return mockMvc.perform(get("/api/analytics/" + path).param("from", from).param("to", to)
                .header("Authorization", bearer));
    }

    @Test
    void merchantAndCategorySharesUseSameTotal() throws Exception {
        UUID blinkit = merchantRepository.save(new Merchant("Blinkit", "Shopping")).getId();
        UUID swiggy = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
        merchantIds.addAll(List.of(blinkit, swiggy));
        UUID person = personRepository.save(new Person(alice, "Ravi")).getId();
        save("2026-11-10", "102.00", blinkit, null);
        save("2026-11-11", "300.00", null, person);
        save("2026-11-12", "598.00", swiggy, null);

        call("merchants", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend").value(1000.00))
                .andExpect(jsonPath("$.items[?(@.name == 'Blinkit')].sharePercent").value(10.20));
        call("categories", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend").value(1000.00))
                .andExpect(jsonPath("$.items[?(@.category == 'Shopping')].sharePercent").value(10.20));
    }

    @Test
    void partialMonthIsProrated() throws Exception {
        save("2026-10-15", "3100.00", null, null);
        save("2026-11-15", "2100.00", null, null);

        call("trends", "2026-10-01", "2026-11-21").andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(2))
                .andExpect(jsonPath("$.months[1].outflow").value(2100.00))
                .andExpect(jsonPath("$.months[1].changePercent").value(0.00));
    }

    @Test
    void emptyRangeShares() throws Exception {
        call("merchants", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalSpend").value(0.00));
        call("categories", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalSpend").value(0.00));
    }
}
