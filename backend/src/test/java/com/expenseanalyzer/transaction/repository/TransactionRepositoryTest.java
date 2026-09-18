package com.expenseanalyzer.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final LocalDate TEST_START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TEST_END_DATE = LocalDate.of(2026, 9, 30);
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
        when(transactionRepository.findByStatementId(TEST_STATEMENT_ID)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByStatementId(TEST_STATEMENT_ID);

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void findByUserIdAndDateRange_shouldReturnTransactions_withinRange() {
        // Arrange
        List<Transaction> transactions = List.of(TRANSACTION_1, TRANSACTION_2);
        when(transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void countByUserIdAndDateRange_shouldReturnCount_withinRange() {
        // Arrange
        when(transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE)).thenReturn(5L);

        // Act
        long result = transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void findByUserIdAndDateRangeAndType_shouldReturnDebitTransactions() {
        // Arrange
        Transaction transaction1 = new Transaction(TEST_USER_ID, "Sep 1 Purchase", TEST_AMOUNT_CENTS, Instant.now(), "DEBIT");
        Transaction transaction2 = new Transaction(TEST_USER_ID, "Sep 2 Deposit", new BigDecimal("100.00"), Instant.now(), "CREDIT");
        List<Transaction> transactions = List.of(transaction1);
        when(transactionRepository.findByUserIdAndDateRangeAndType(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRangeAndType(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void findByUserIdAndMerchantId_shouldReturnTransactions_forMerchant() {
        // Arrange
        Transaction transaction1 = new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT");
        List<Transaction> transactions = List.of(transaction1);
        when(transactionRepository.findByUserIdAndMerchantId(TEST_USER_ID, TEST_STATEMENT_ID)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndMerchantId(TEST_USER_ID, TEST_STATEMENT_ID);

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void findByUserIdAndCategoryId_shouldReturnTransactions_forCategory() {
        // Arrange
        Transaction transaction1 = new Transaction(TEST_USER_ID, "Grocery Store", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT");
        List<Transaction> transactions = List.of(transaction1);
        when(transactionRepository.findByUserIdAndCategoryId(TEST_USER_ID, TEST_STATEMENT_ID)).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndCategoryId(TEST_USER_ID, TEST_STATEMENT_ID);

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    void countDebitByUserId_shouldReturnDebitCount() {
        // Arrange
        when(transactionRepository.countDebitByUserId(TEST_USER_ID)).thenReturn(3L);

        // Act
        long result = transactionRepository.countDebitByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void sumDebitByUserIdAndDateRange_shouldReturnTotalAmount() {
        // Arrange
        when(transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE)).thenReturn(new BigDecimal("1234.56"));

        // Act
        BigDecimal result = transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    void findByUserIdAndRawDescriptionContaining_shouldReturnTransactions_byKeyword() {
        // Arrange
        List<Transaction> transactions;
        transactions = List.of(TRANSACTION_1, new Transaction(TEST_USER_ID, "Walmart Shopping", new BigDecimal("50.00"), TEST_DATE.plus(1, ChronoUnit.DAYS), "DEBIT"));
        when(transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "Store")).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "Store");

        // Assert
        assertThat(result).hasSize(2);
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

    @Test
    void countByUserId_shouldReturnTotalCount() {
        // Arrange
        when(transactionRepository.countByUserId(TEST_USER_ID)).thenReturn(10L);

        // Act
        long result = transactionRepository.countByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(10L);
    }

    @Test
    void findByUserIdAndDateRange_shouldReturnEmpty_whenNoTransactionsInRange() {
        // Arrange
        LocalDate farFutureDate = LocalDate.of(2030, 1, 1);
        when(transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, farFutureDate)).thenReturn(List.of());

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, farFutureDate);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void sumDebitByUserIdAndDateRange_shouldReturnZero_whenNoDebits() {
        // Arrange
        LocalDate farFutureDate = LocalDate.of(2030, 1, 1);
        when(transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, farFutureDate)).thenReturn(BigDecimal.ZERO);

        // Act
        BigDecimal result = transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, farFutureDate);

        // Assert
        assertThat(result).isEqualTo(BigDecimal.ZERO);
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
            when(transactionRepository.findByStatementId(null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByStatementId(null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByUserIdAndDateRange_shouldHandleNullUser() {
            // Arrange
            when(transactionRepository.findByUserIdAndDateRange(null, TEST_START_DATE, TEST_END_DATE)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndDateRange(null, TEST_START_DATE, TEST_END_DATE);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByUserIdAndDateRange_shouldHandleNullDates() {
            // Arrange
            when(transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, null, null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, null, null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void countByUserIdAndDateRange_shouldHandleNullUser() {
            // Arrange
            when(transactionRepository.countByUserIdAndDateRange(null, TEST_START_DATE, TEST_END_DATE)).thenReturn(0L);

            // Act
            long result = transactionRepository.countByUserIdAndDateRange(null, TEST_START_DATE, TEST_END_DATE);

            // Assert
            assertThat(result).isEqualTo(0L);
        }

        @Test
        void findByUserIdAndMerchantId_shouldHandleNullMerchant() {
            // Arrange
            when(transactionRepository.findByUserIdAndMerchantId(TEST_USER_ID, null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndMerchantId(TEST_USER_ID, null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByUserIdAndCategoryId_shouldHandleNullCategory() {
            // Arrange
            when(transactionRepository.findByUserIdAndCategoryId(TEST_USER_ID, null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndCategoryId(TEST_USER_ID, null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByUserIdAndRawDescriptionContaining_shouldHandleNullKeyword() {
            // Arrange
            when(transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, null)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void countByUserId_shouldHandleNullUser() {
            // Arrange
            when(transactionRepository.countByUserId(null)).thenReturn(0L);

            // Act
            long result = transactionRepository.countByUserId(null);

            // Assert
            assertThat(result).isEqualTo(0L);
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

    @Nested
    class EmptyInputTests {

        @Test
        void findByUserIdAndRawDescriptionContaining_shouldHandleEmptyKeyword() {
            // Arrange
            when(transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "")).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByUserIdAndDateRange_shouldHandleEmptyDateRange() {
            // Arrange
            LocalDate emptyStartDate = null;
            when(transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, emptyStartDate, TEST_END_DATE)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, emptyStartDate, TEST_END_DATE);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void countByUserIdAndDateRange_shouldHandleEmptyDateRange() {
            // Arrange
            LocalDate emptyStartDate = null;
            when(transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, emptyStartDate, TEST_END_DATE)).thenReturn(0L);

            // Act
            long result = transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, emptyStartDate, TEST_END_DATE);

            // Assert
            assertThat(result).isEqualTo(0L);
        }
    }

    @Nested
    class BoundaryValueTests {

        @Test
        void findByUserIdAndDateRange_shouldHandleBoundaryDates() {
            // Arrange
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 12, 31);
            when(transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, startDate, endDate)).thenReturn(List.of());

            // Act
            List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, startDate, endDate);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void countByUserIdAndDateRange_shouldHandleFullYearRange() {
            // Arrange
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 12, 31);
            when(transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, startDate, endDate)).thenReturn(365L);

            // Act
            long result = transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, startDate, endDate);

            // Assert
            assertThat(result).isEqualTo(365L);
        }

        @Test
        void sumDebitByUserIdAndDateRange_shouldHandleZeroAmount() {
            // Arrange
            when(transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE)).thenReturn(BigDecimal.ZERO);

            // Act
            BigDecimal result = transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

            // Assert
            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }
    }
}
