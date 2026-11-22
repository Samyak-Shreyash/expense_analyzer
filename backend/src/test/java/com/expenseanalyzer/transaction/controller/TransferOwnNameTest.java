package com.expenseanalyzer.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.service.TransferService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Transfers to the user's own name: paired even with a person, marked when unpaired, manual choices kept. */
@SpringBootTest
class TransferOwnNameTest {

    private static final Instant AUG_3 = Instant.parse("2026-08-03T00:00:00Z");

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransferService transferService;

    @MockitoBean
    private UserRepository userRepository;

    private User user;
    private UUID accountA;
    private UUID accountB;
    private Instant created = Instant.parse("2026-09-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        user.setRole(UserRole.USER);
        user.setFullName("Samyak Shreyash");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        accountA = statementRepository.saveAndFlush(new Statement(user.getId(), "1111", "HDFC")).getId();
        accountB = statementRepository.saveAndFlush(new Statement(user.getId(), "2222", "SBI")).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
    }

    private Transaction tx(UUID statementId, String type, String amount, String description) {
        Transaction t = new Transaction();
        t.setRawDescription(description);
        t.setAmountPaise(new BigDecimal(amount).movePointRight(2));
        t.setDate(AUG_3);
        t.setType(type);
        t.setUserId(user.getId());
        t.setStatementId(statementId);
        created = created.plusSeconds(1);
        t.setCreatedAt(created);
        t.setUpdatedAt(created);
        return transactionRepository.saveAndFlush(t);
    }

    private Transaction reload(Transaction t) {
        return transactionRepository.findById(t.getId()).orElseThrow();
    }

    @Test
    void ownNameRowsWithAPersonStillPair() {
        Transaction out = tx(accountA, "DEBIT", "40000.00", "SAMYAK SHREYASH UTIB");
        out.setPersonId(UUID.randomUUID());
        transactionRepository.saveAndFlush(out);
        Transaction in = tx(accountB, "CREDIT", "40000.00", "SAMYAKSH HDFCB ANK");

        transferService.match(user.getId());

        assertThat(reload(out).isTransfer()).isTrue();
        assertThat(reload(in).isTransfer()).isTrue();
        assertThat(reload(out).getTransferPairId()).isEqualTo(in.getId());
        assertThat(reload(in).getTransferPairId()).isEqualTo(out.getId());
    }

    @Test
    void unpairedOwnNameRowIsATransfer() {
        Transaction row = tx(accountA, "CREDIT", "4000.00", "Samyaksh");

        transferService.match(user.getId());

        assertThat(reload(row).isTransfer()).isTrue();
        assertThat(reload(row).getTransferPairId()).isNull();
        assertThat(reload(row).isTransferManual()).isFalse();
    }

    @Test
    void manualUnmarkIsRespected() {
        Transaction row = tx(accountA, "CREDIT", "4000.00", "Samyaksh");
        row.setTransferManual(true);
        transactionRepository.saveAndFlush(row);

        transferService.match(user.getId());

        assertThat(reload(row).isTransfer()).isFalse();
        assertThat(reload(row).isTransferManual()).isTrue();
    }

    @Test
    void categorisedOwnNameRowsAreNotTransfers() {
        Transaction emi = tx(accountA, "DEBIT", "9000.00", "NEFT-EMI SAMYAK SHREYASH");
        UUID loanEmi = UUID.randomUUID();
        emi.setCategoryId(loanEmi);
        transactionRepository.saveAndFlush(emi);
        Transaction credit = tx(accountA, "CREDIT", "100.00", "Samyaksh");
        credit.setCategoryId(UUID.randomUUID());
        transactionRepository.saveAndFlush(credit);
        Transaction loan = tx(accountA, "CREDIT", "200.00", "Samyaksh");
        loan.setLoanId(UUID.randomUUID());
        transactionRepository.saveAndFlush(loan);

        transferService.match(user.getId());

        assertThat(reload(emi).isTransfer()).isFalse();
        assertThat(reload(emi).getCategoryId()).isEqualTo(loanEmi);
        assertThat(reload(credit).isTransfer()).isFalse();
        assertThat(reload(loan).isTransfer()).isFalse();
    }

    @Test
    void otherPeopleStillExcluded() {
        Transaction out = tx(accountA, "DEBIT", "500.00", "RAHUL SHARMA");
        out.setPersonId(UUID.randomUUID());
        transactionRepository.saveAndFlush(out);
        Transaction in = tx(accountB, "CREDIT", "500.00", "credit");

        transferService.match(user.getId());

        assertThat(reload(out).isTransfer()).isFalse();
        assertThat(reload(in).isTransfer()).isFalse();
    }
}
