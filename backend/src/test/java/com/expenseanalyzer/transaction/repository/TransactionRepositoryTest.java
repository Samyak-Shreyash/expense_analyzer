package com.expenseanalyzer.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.UUID;
import static org.springframework.data.domain.PageRequest.of;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TransactionRepository.
 */
@ExtendWith(MockitoExtension.class)
class TransactionRepositoryTest {

    @Spy
    private TransactionRepository transactionRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_STATEMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final Instant TEST_DATE = Instant.parse("2026-09-01T12:00:00Z");
    private static final BigDecimal TEST_AMOUNT_CENTS = new BigDecimal("1234");

    // Test transactions for use in tests that need multiple transactions
    private static final Transaction TRANSACTION_1 = new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT");
    private static final Transaction TRANSACTION_2 = new Transaction(TEST_USER_ID, "Grocery Store", new BigDecimal("50.00"), TEST_DATE.plus(1,ChronoUnit.DAYS), "DEBIT");

    @Test
    void findByUserId_shouldReturnTransactions_whenUserExists() {
        // Arrange
        Transaction transaction1 = new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT");
        Transaction transaction2 = new Transaction(TEST_USER_ID, "Grocery Store", new BigDecimal("50.00"), TEST_DATE.plus(1, ChronoUnit.DAYS), "DEBIT");
        List<Transaction> transactions = List.of(transaction1, transaction2);
        when(transactionRepository.findByUserId(TEST_USER_ID)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenUserNotFound() {
        // Arrange
        when(transactionRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of());

        // Act
        List<Transaction> result = transactionRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByStatementId_shouldReturnTransactions_whenStatementExists() {
        // Arrange
        Transaction transaction1 = new Transaction(TEST_STATEMENT_ID, "Bank Transfer", TEST_AMOUNT_CENTS, TEST_DATE, "CREDIT");
        List<Transaction> transactions = List.of(transaction1);
        when(transactionRepository.findByUserIdAndStatementId(TEST_USER_ID, TEST_STATEMENT_ID)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndStatementId(TEST_USER_ID, TEST_STATEMENT_ID);

        // Assert
        assertThat(result).hasSize(1);
    }









    @Test
    void findAll_shouldReturnList_whenPageableProvided() {
        // Arrange
        List<Transaction> transactions = List.of(TRANSACTION_1, TRANSACTION_2);
        Pageable pageable = of(0, 10);
        when(transactionRepository.findAll(pageable)).thenReturn(new PageImpl<>(transactions));

        // Act
        Page<Transaction> result = transactionRepository.findAll(pageable);

        // Assert
        assertThat(result).hasSize(2);
    }




    @Nested
    class NullInputTests {

        @Test
        void findByUserId_shouldHandleNullUser() {
            // Arrange
            when(transactionRepository.findByUserId(null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserId(null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByStatementId_shouldHandleNullStatement() {
            // Arrange
            when(transactionRepository.findByUserIdAndStatementId(TEST_USER_ID, null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndStatementId(TEST_USER_ID, null);

            // Assert
            assertThat(result).isEmpty();
        }








        @Test
        void findAll_shouldHandleNullPageable() {
            // Arrange
            List<Transaction> emptyTransactions = List.of();
            when(transactionRepository.findAll(Pageable.unpaged())).thenReturn(new PageImpl<>(emptyTransactions));

            // Act
            Page<Transaction> result = transactionRepository.findAll(Pageable.unpaged());

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
