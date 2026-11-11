package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.LoanPayeeRepository;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Loans and their EMIs: linking, the lender's default loan, interest, closing, and their effect on analytics. */
@SpringBootTest
class LoanTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String LENDER = "HDFC LOAN EMI";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private LoanPayeeRepository loanPayeeRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User alice;
    private User bob;
    private UUID loanCategory;
    private UUID emiCategory;
    /** Strictly increasing creation times, so same-day EMIs repay in a fixed order. */
    private Instant created = Instant.parse("2026-02-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user(ALICE);
        bob = user(BOB);
        loanCategory = categoryRepository.save(new Category(alice.getId(), "Loan", false)).getId();
        emiCategory = categoryRepository.save(new Category(alice.getId(), "Loan EMI", false)).getId();
    }

    @AfterEach
    void cleanUp() {
        loanPayeeRepository.deleteAll();
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private UUID tx(User owner, String type, String amount, String day, String payee, UUID categoryId) {
        Transaction t = new Transaction();
        t.setRawDescription(payee);
        t.setNormalizedDescription(payee);
        t.setAmountCents(new BigDecimal(amount).movePointRight(2));
        t.setDate(Instant.parse(day + "T00:00:00Z"));
        t.setType(type);
        t.setUserId(owner.getId());
        t.setCategoryId(categoryId);
        created = created.plusSeconds(1);
        t.setCreatedAt(created);
        t.setUpdatedAt(created);
        return transactionRepository.saveAndFlush(t).getId();
    }

    private Transaction reload(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private void link(String email, UUID emi, UUID loan, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + emi + "/loan").header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content("{\"loanId\":" + (loan == null ? "null" : "\"" + loan + "\"") + "}"))
                .andExpect(status().is(expected));
    }

    private void close(UUID loan, boolean closed, int expected) throws Exception {
        mockMvc.perform(put("/api/transactions/" + loan + "/loan-closed").header("Authorization", bearer(ALICE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"closed\":" + closed + "}"))
                .andExpect(status().is(expected));
    }

    @Test
    void linkingOneEmiLinksTheLendersOthersAndWhatIsPaidBeyondThePrincipalIsInterest() throws Exception {
        UUID loan = tx(alice, "CREDIT", "1000.00", "2026-01-01", "LOAN DISBURSAL", loanCategory);
        UUID first = tx(alice, "DEBIT", "400.00", "2026-01-05", LENDER, emiCategory);
        UUID second = tx(alice, "DEBIT", "400.00", "2026-01-15", LENDER, emiCategory);
        UUID third = tx(alice, "DEBIT", "400.00", "2026-01-25", LENDER, emiCategory);
        UUID other = tx(alice, "DEBIT", "400.00", "2026-01-25", "SOME SHOP", null);

        link(ALICE, second, loan, 200);

        assertThat(reload(first).getLoanId()).isEqualTo(loan);
        assertThat(reload(third).getLoanId()).isEqualTo(loan);
        assertThat(reload(other).getLoanId()).isNull();
        assertThat(reload(first).getLoanInterestPaise()).isNull();
        assertThat(reload(second).getLoanInterestPaise()).isNull();
        assertThat(reload(third).getLoanInterestPaise()).isEqualByComparingTo("20000");
        mockMvc.perform(get("/api/transactions").param("loanId", loan.toString()).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.totalItems").value(3));
        mockMvc.perform(get("/api/transactions/" + third).header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.loanId").value(loan.toString()))
                .andExpect(jsonPath("$.loanInterest").value(200.00));

        link(ALICE, third, null, 200);
        assertThat(reload(third).getLoanId()).isNull();
        assertThat(reload(third).getLoanInterestPaise()).isNull();
    }

    @Test
    void loansAreNotIncomeAndOnlyInterestIsSpending() throws Exception {
        UUID loan = tx(alice, "CREDIT", "1000.00", "2026-01-01", "LOAN DISBURSAL", loanCategory);
        tx(alice, "CREDIT", "5000.00", "2026-01-02", "SALARY", null);
        UUID emi = tx(alice, "DEBIT", "700.00", "2026-01-05", LENDER, emiCategory);
        tx(alice, "DEBIT", "700.00", "2026-01-15", LENDER, emiCategory);
        link(ALICE, emi, loan, 200);

        mockMvc.perform(get("/api/analytics/summary").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflow").value(5000.00))
                .andExpect(jsonPath("$.loanReceived").value(1000.00))
                .andExpect(jsonPath("$.outflow").value(400.00))
                .andExpect(jsonPath("$.nonSpendingOutflow").value(1000.00))
                .andExpect(jsonPath("$.net").value(4600.00));
        mockMvc.perform(get("/api/analytics/categories").param("from", "2026-01-01").param("to", "2026-01-31")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(jsonPath("$.items[?(@.category=='Loan interest')].spend").value(400.00));
    }

    @Test
    void onlyAnEmiLinksAndOnlyToAnOpenLoanOfYourOwn() throws Exception {
        UUID loan = tx(alice, "CREDIT", "1000.00", "2026-01-01", "LOAN DISBURSAL", loanCategory);
        UUID notALoan = tx(alice, "CREDIT", "1000.00", "2026-01-01", "SALARY", null);
        UUID emi = tx(alice, "DEBIT", "400.00", "2026-01-05", LENDER, emiCategory);
        UUID notAnEmi = tx(alice, "DEBIT", "400.00", "2026-01-05", "SOME SHOP", null);
        UUID bobs = tx(bob, "CREDIT", "1000.00", "2026-01-01", "LOAN DISBURSAL", null);

        link(ALICE, notAnEmi, loan, 400);
        link(ALICE, emi, notALoan, 400);
        link(ALICE, emi, bobs, 404);
        link(BOB, emi, loan, 404);
        close(loan, true, 200);
        link(ALICE, emi, loan, 400);
        assertThat(reload(emi).getLoanId()).isNull();
    }

    @Test
    void closingALoanDropsTheLendersDefaultAndKeepsItsEmisAsEmis() throws Exception {
        UUID loan = tx(alice, "CREDIT", "1000.00", "2026-01-01", "LOAN DISBURSAL", loanCategory);
        UUID emi = tx(alice, "DEBIT", "400.00", "2026-01-05", LENDER, emiCategory);
        link(ALICE, emi, loan, 200);
        assertThat(loanPayeeRepository.findByLoanId(loan)).hasSize(1);

        close(loan, true, 200);

        assertThat(reload(loan).isLoanClosed()).isTrue();
        assertThat(loanPayeeRepository.findByLoanId(loan)).isEmpty();
        assertThat(reload(emi).getLoanId()).isEqualTo(loan);
        assertThat(reload(emi).getCategoryId()).isEqualTo(emiCategory);

        close(loan, false, 200);
        assertThat(reload(loan).isLoanClosed()).isFalse();
        assertThat(loanPayeeRepository.findByLoanId(loan)).hasSize(1);
        close(emi, true, 400);
    }
}
