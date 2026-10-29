package com.expenseanalyzer.analytics.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AnalyticsControllerTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID alice;
    private UUID bob;
    private final List<UUID> merchantIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(userRepository.claimCategorySeeding(org.mockito.ArgumentMatchers.any())).thenReturn(1); // mocked repo: always claim
        alice = user(ALICE);
        bob = user(BOB);
        UUID swiggy = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
        UUID amazon = merchantRepository.save(new Merchant("Amazon", "Shopping")).getId();
        merchantIds.addAll(List.of(swiggy, amazon));

        save(alice, "2025-12-10", "1000.00", "DEBIT", swiggy);   // previous period
        save(alice, "2026-01-01", "500.00", "DEBIT", swiggy);    // first day: included
        save(alice, "2026-01-15", "100.00", "CREDIT", swiggy);   // refund
        save(alice, "2026-01-20", "2000.00", "DEBIT", amazon);
        save(alice, "2026-01-25", "300.00", "DEBIT", null);      // unmatched spend
        save(alice, "2026-01-31", "85000.00", "CREDIT", null);   // last day: included
        save(alice, "2026-02-01", "20000.00", "DEBIT", null);    // after range
        save(bob, "2026-01-10", "999.00", "DEBIT", swiggy);      // someone else
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : List.of(alice, bob)) { // only this test's users
            jdbcTemplate.update("DELETE FROM transactions WHERE user_id = ?", id);
        }
        merchantRepository.deleteAllById(merchantIds);
        merchantIds.clear();
    }

    private UUID user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u.getId();
    }

    private static Transaction tx(UUID user, String date, String rupees, String type, UUID merchant) {
        Transaction t = new Transaction(user, "tx", new BigDecimal(rupees).movePointRight(2),
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant(), type);
        t.setMerchantId(merchant);
        return t;
    }

    private void save(UUID user, String date, String rupees, String type, UUID merchant) {
        transactionRepository.save(tx(user, date, rupees, type, merchant));
    }

    private ResultActions call(String path, String from, String to) throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        return mockMvc.perform(get("/api/analytics/" + path).param("from", from).param("to", to)
                .header("Authorization", bearer));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryTotalsInclusivePeriodAndComparesWithPreviousPeriod() throws Exception {
        call("summary", "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.inflow").value(85100.00))
                .andExpect(jsonPath("$.outflow").value(2800.00))
                .andExpect(jsonPath("$.net").value(82300.00))
                .andExpect(jsonPath("$.transactionCount").value(5))
                .andExpect(jsonPath("$.previous.from").value("2025-12-01"))
                .andExpect(jsonPath("$.previous.to").value("2025-12-31"))
                .andExpect(jsonPath("$.previous.outflow").value(1000.00))
                .andExpect(jsonPath("$.outflowChangePercent").value(180.00))
                .andExpect(jsonPath("$.inflowChangePercent").value(nullValue())); // previous inflow is 0
    }

    @Test
    void emptyPeriodIsAllZeros() throws Exception {
        call("summary", "2024-01-01", "2024-01-31")
                .andExpect(jsonPath("$.inflow").value(0))
                .andExpect(jsonPath("$.outflow").value(0))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.outflowChangePercent").value(nullValue()));
    }

    @Test
    void merchantSpendIsNetOfRefundsAndSortedDescending() throws Exception {
        call("merchants", "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend").value(2700.00))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Amazon"))
                .andExpect(jsonPath("$.items[0].spend").value(2000.00))
                .andExpect(jsonPath("$.items[0].sharePercent").value(74.07))
                .andExpect(jsonPath("$.items[1].name").value("Swiggy"))
                .andExpect(jsonPath("$.items[1].spend").value(400.00))
                .andExpect(jsonPath("$.items[1].transactionCount").value(2))
                .andExpect(jsonPath("$.items[2].merchantId").value(nullValue()))
                .andExpect(jsonPath("$.items[2].name").value("Unmatched"))
                .andExpect(jsonPath("$.items[2].spend").value(300.00));
    }

    @Test
    void categorySpendGroupsByMerchantCategoryHint() throws Exception {
        call("categories", "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("Shopping"))
                .andExpect(jsonPath("$.items[0].spend").value(2000.00))
                .andExpect(jsonPath("$.items[1].category").value("Food & Dining"))
                .andExpect(jsonPath("$.items[1].spend").value(400.00))
                .andExpect(jsonPath("$.items[2].category").value("Uncategorized"))
                .andExpect(jsonPath("$.items[2].spend").value(300.00));
    }

    @Test
    void cashFlowHasOneBucketPerMonthIncludingEmptyMonths() throws Exception {
        call("cash-flow", "2025-11-01", "2026-02-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(4))
                .andExpect(jsonPath("$.months[0].month").value("2025-11"))
                .andExpect(jsonPath("$.months[0].net").value(0))
                .andExpect(jsonPath("$.months[1].month").value("2025-12"))
                .andExpect(jsonPath("$.months[1].outflow").value(1000.00))
                .andExpect(jsonPath("$.months[1].net").value(-1000.00))
                .andExpect(jsonPath("$.months[2].inflow").value(85100.00))
                .andExpect(jsonPath("$.months[2].outflow").value(2800.00))
                .andExpect(jsonPath("$.months[2].net").value(82300.00))
                .andExpect(jsonPath("$.months[3].outflow").value(20000.00));
    }

    @Test
    void trendsCompareEachMonthWithThePreviousOne() throws Exception {
        call("trends", "2025-12-01", "2026-02-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].month").value("2025-12"))
                .andExpect(jsonPath("$.months[0].outflow").value(1000.00))
                .andExpect(jsonPath("$.months[0].changePercent").value(nullValue()))
                .andExpect(jsonPath("$.months[1].changePercent").value(180.00))
                .andExpect(jsonPath("$.months[2].changePercent").value(614.29));
    }

    @Test
    void partialMonthsAreClippedToTheRequestedRange() throws Exception {
        call("cash-flow", "2026-01-16", "2026-02-01")
                .andExpect(jsonPath("$.months.length()").value(2))
                .andExpect(jsonPath("$.months[0].outflow").value(2300.00)) // 20th and 25th only
                .andExpect(jsonPath("$.months[1].outflow").value(20000.00));
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        call("summary", "2026-02-01", "2026-01-01").andExpect(status().isBadRequest());
        call("cash-flow", "2015-01-01", "2026-01-01").andExpect(status().isBadRequest()); // over 5 years
        String bearer = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        mockMvc.perform(get("/api/analytics/summary").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregationsStayFastOnAYearOfHeavyUsage() throws Exception {
        List<Transaction> bulk = new ArrayList<>();
        LocalDate day = LocalDate.of(2023, 1, 1);
        for (int i = 0; i < 20_000; i++) {
            bulk.add(tx(alice, day.plusDays(i % 365).toString(), "123.45", i % 10 == 0 ? "CREDIT" : "DEBIT", null));
        }
        transactionRepository.saveAll(bulk);

        long start = System.nanoTime();
        call("summary", "2023-01-01", "2023-12-31").andExpect(jsonPath("$.transactionCount").value(20_000));
        call("merchants", "2023-01-01", "2023-12-31").andExpect(status().isOk());
        call("cash-flow", "2023-01-01", "2023-12-31").andExpect(jsonPath("$.months.length()").value(12));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(3_000);
    }
}
