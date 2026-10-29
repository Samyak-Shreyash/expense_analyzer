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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spending vs non-spending categories: effective category and the countsAsSpending flag drive analytics. */
@SpringBootTest
class AnalyticsNonSpendingTest {

    private static final String ALICE = "alice-ns@example.com";

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
    private CategoryRepository categoryRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private UserMerchantCategoryRepository mappingRepository;

    @MockitoBean
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private UUID alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(ALICE);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(ALICE)).thenReturn(Optional.of(u));
        alice = u.getId();

        UUID repayment = defaultCategory("Repayment");
        UUID loan = defaultCategory("Loan");
        UUID savings = categoryRepository.save(new Category(alice, "Savings", false)).getId();
        UUID swiggy = merchantRepository.save(new Merchant("Swiggy", "Food & Dining")).getId();
        UUID amazon = merchantRepository.save(new Merchant("Amazon", "Shopping")).getId();
        mappingRepository.save(new UserMerchantCategory(alice, amazon, savings));
        Person ravi = new Person(alice, "Ravi");
        ravi.setCategoryId(repayment);
        UUID raviId = personRepository.save(ravi).getId();

        save("500.00", "DEBIT", swiggy, null, null);     // food: spending
        save("300.00", "DEBIT", swiggy, null, loan);     // override moves it out of Food into Loan
        save("700.00", "DEBIT", amazon, null, null);     // user category Savings (countsAsSpending=false)
        save("1000.00", "DEBIT", null, raviId, null);    // transfer to a person: Repayment
        save("200.00", "CREDIT", null, raviId, null);    // received back: cash flow only
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        mappingRepository.deleteAll();
        personRepository.deleteAll();
        merchantRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private UUID defaultCategory(String name) {
        return categoryService.ensureDefaults(alice).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private void save(String rupees, String type, UUID merchant, UUID person, UUID category) {
        Transaction t = new Transaction(alice, "tx", new BigDecimal(rupees).movePointRight(2),
                LocalDate.parse("2026-01-10").atStartOfDay(ZoneOffset.UTC).toInstant(), type);
        t.setMerchantId(merchant);
        t.setPersonId(person);
        t.setCategoryId(category);
        transactionRepository.save(t);
    }

    private ResultActions call(String path) throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(ALICE, null));
        return mockMvc.perform(get("/api/analytics/" + path).param("from", "2026-01-01").param("to", "2026-01-31")
                .header("Authorization", bearer));
    }

    @Test
    void nonSpendingTransfersAreExcludedFromSpendingButStayInCashFlow() throws Exception {
        call("summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.outflow").value(500.00))
                .andExpect(jsonPath("$.inflow").value(200.00))
                .andExpect(jsonPath("$.transactionCount").value(5));
        call("cash-flow").andExpect(jsonPath("$.months[0].outflow").value(2500.00))
                .andExpect(jsonPath("$.months[0].inflow").value(200.00));
        call("trends").andExpect(jsonPath("$.months[0].outflow").value(500.00));
        call("categories").andExpect(jsonPath("$.totalSpend").value(500.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].category").value("Food & Dining"));
        call("merchants").andExpect(jsonPath("$.totalSpend").value(500.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Swiggy"));
    }

    @Test
    void nonSpendingBreakdownGroupsByCategoryThenPerson() throws Exception {
        call("non-spending").andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.total").value(2000.00))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].category").value("Repayment"))
                .andExpect(jsonPath("$.items[0].spend").value(1000.00))
                .andExpect(jsonPath("$.items[0].sharePercent").value(50.00))
                .andExpect(jsonPath("$.items[0].transactionCount").value(1))
                .andExpect(jsonPath("$.items[0].people.length()").value(1))
                .andExpect(jsonPath("$.items[0].people[0].name").value("Ravi"))
                .andExpect(jsonPath("$.items[0].people[0].spend").value(1000.00))
                .andExpect(jsonPath("$.items[1].category").value("Savings"))
                .andExpect(jsonPath("$.items[1].spend").value(700.00))
                .andExpect(jsonPath("$.items[1].people.length()").value(0))
                .andExpect(jsonPath("$.items[2].category").value("Loan"))
                .andExpect(jsonPath("$.items[2].spend").value(300.00));
    }
}
