package com.expenseanalyzer.analytics.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.category.service.CategoryService;
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

/** Net and monthly cash flow follow the summary's inflow (no loans) and outflow (spending). */
@SpringBootTest
class AnalyticsNetTest {

    private static final String ALICE = "alice-net@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID alice;

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

        UUID loan = defaultCategory("Loan");
        UUID loanEmi = defaultCategory("Loan EMI");
        save("2026-11-20", "1000.00", "CREDIT", null);   // salary
        save("2026-11-20", "500.00", "CREDIT", loan);    // loan received: not income
        save("2026-11-20", "300.00", "DEBIT", null);     // uncategorised: spending
        save("2026-11-20", "200.00", "DEBIT", loanEmi);  // EMI: non-spending
    }

    @AfterEach
    void cleanUp() {
        for (String table : List.of("transactions", "categories")) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", alice); // only this test's user
        }
    }

    private UUID defaultCategory(String name) {
        return categoryService.ensureDefaults(alice).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private void save(String date, String rupees, String type, UUID category) {
        Transaction t = new Transaction(alice, "tx", new BigDecimal(rupees).movePointRight(2),
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant(), type);
        t.setCategoryId(category);
        transactionRepository.save(t);
    }

    private ResultActions call(String path, String from, String to) throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        return mockMvc.perform(get("/api/analytics/" + path).param("from", from).param("to", to)
                .header("Authorization", bearer));
    }

    @Test
    void netIsInflowMinusOutflow() throws Exception {
        call("summary", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.inflow").value(1000.00))
                .andExpect(jsonPath("$.outflow").value(300.00))
                .andExpect(jsonPath("$.net").value(700.00))
                .andExpect(jsonPath("$.loanReceived").value(500.00))
                .andExpect(jsonPath("$.nonSpendingOutflow").value(200.00));
    }

    @Test
    void cashFlowMatchesSummary() throws Exception {
        call("cash-flow", "2026-11-01", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.months[0].month").value("2026-11"))
                .andExpect(jsonPath("$.months[0].inflow").value(1000.00))
                .andExpect(jsonPath("$.months[0].outflow").value(300.00))
                .andExpect(jsonPath("$.months[0].net").value(700.00));
    }

    @Test
    void cashFlowClipsToRange() throws Exception {
        save("2026-11-10", "100.00", "DEBIT", null); // before the range
        call("cash-flow", "2026-11-15", "2026-11-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.months[0].outflow").value(300.00));
    }
}
