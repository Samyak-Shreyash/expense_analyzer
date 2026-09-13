package com.expenseanalyzer.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for TransactionRepository.
 */
@ExtendWith(MockitoExtension.class)
class TransactionRepositoryTest {

    @Mock
    private com.expenseanalyzer.transaction.repository.TransactionRepository transactionRepositoryMock;

    @InjectMocks
    private TransactionRepository transactionRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_STATEMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_MERCHANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final String TEST_USER_EMAIL = "test@example.com";
    private static final String TEST_MERCHANT_NAME = "Test Merchant";
    private static final String TEST_CATEGORY_NAME = "Food & Dining";
    private static final BigDecimal TEST_AMOUNT_CENTS = new BigDecimal("1234");
    private static final Instant TEST_DATE = Instant.parse("2026-09-01T12:00:00Z");
    private static final LocalDate TEST_START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TEST_END_DATE = LocalDate.of(2026, 9, 30);

    @Test
    void findByUserId_shouldReturnTransactions_whenUserExists() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT"),
            new Transaction(TEST_USER_ID, "Grocery Store", new BigDecimal("50.00"), TEST_DATE.plusDays(1), "DEBIT")
        );
        when(transactionRepository.findByUserId(any(UUID.class))).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRawDescription()).isEqualTo("Amazon Purchase");
        verify(transactionRepositoryMock).findByUserId(any(UUID.class));
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenUserNotFound() {
        // Arrange
        when(transactionRepository.findByUserId(any(UUID.class))).thenReturn(List.of());

        // Act
        List<Transaction> result = transactionRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verify(transactionRepositoryMock).findByUserId(any(UUID.class));
    }

    @Test
    void findByStatementId_shouldReturnTransactions_whenStatementExists() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_STATEMENT_ID, "Bank Transfer", TEST_AMOUNT_CENTS, TEST_DATE, "CREDIT")
        );
        when(transactionRepository.findByStatementId(any(UUID.class))).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByStatementId(TEST_STATEMENT_ID);

        // Assert
        assertThat(result).hasSize(1);
        verify(transactionRepositoryMock).findByStatementId(any(UUID.class));
    }

    @Test
    void findByUserIdAndDateRange_shouldReturnTransactions_withinRange() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Sep 1 Purchase", TEST_AMOUNT_CENTS, TEST_START_DATE.atStartOfDay(), "DEBIT"),
            new Transaction(TEST_USER_ID, "Sep 2 Purchase", new BigDecimal("100.00"), TEST_END_DATE.atStartOfDay(), "DEBIT")
        );
        when(transactionRepository.findByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).hasSize(2);
        verify(transactionRepositoryMock)
            .findByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void countByUserIdAndDateRange_shouldReturnCount_withinRange() {
        // Arrange
        when(transactionRepository.countByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(5L);

        // Act
        long result = transactionRepository.countByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEqualTo(5L);
        verify(transactionRepositoryMock)
            .countByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void findByUserIdAndDateRangeAndType_shouldReturnDebitTransactions() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Sep 1 Purchase", TEST_AMOUNT_CENTS, TEST_START_DATE.atStartOfDay(), "DEBIT"),
            new Transaction(TEST_USER_ID, "Sep 2 Deposit", new BigDecimal("100.00"), TEST_END_DATE.atStartOfDay(), "CREDIT")
        );
        when(transactionRepository.findByUserIdAndDateRangeAndType(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRangeAndType(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isDebit()).isEqualTo(true);
        verify(transactionRepositoryMock)
            .findByUserIdAndDateRangeAndType(any(UUID.class), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void findByUserIdAndMerchantId_shouldReturnTransactions_forMerchant() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT", TEST_MERCHANT_ID)
        );
        when(transactionRepository.findByUserIdAndMerchantId(any(UUID.class), any(UUID.class))).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndMerchantId(TEST_USER_ID, TEST_MERCHANT_ID);

        // Assert
        assertThat(result).hasSize(1);
        verify(transactionRepositoryMock).findByUserIdAndMerchantId(any(UUID.class), any(UUID.class));
    }

    @Test
    void findByUserIdAndCategoryId_shouldReturnTransactions_forCategory() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Grocery Store", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT", TEST_CATEGORY_ID)
        );
        when(transactionRepository.findByUserIdAndCategoryId(any(UUID.class), any(UUID.class))).thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndCategoryId(TEST_USER_ID, TEST_CATEGORY_ID);

        // Assert
        assertThat(result).hasSize(1);
        verify(transactionRepositoryMock).findByUserIdAndCategoryId(any(UUID.class), any(UUID.class));
    }

    @Test
    void countDebitByUserId_shouldReturnDebitCount() {
        // Arrange
        when(transactionRepository.countDebitByUserId(any(UUID.class))).thenReturn(3L);

        // Act
        long result = transactionRepository.countDebitByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(3L);
        verify(transactionRepositoryMock).countDebitByUserId(any(UUID.class));
    }

    @Test
    void sumDebitByUserIdAndDateRange_shouldReturnTotalAmount() {
        // Arrange
        when(transactionRepository.sumDebitByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new BigDecimal("1234.56"));

        // Act
        BigDecimal result = transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEqualTo(new BigDecimal("1234.56"));
        verify(transactionRepositoryMock)
            .sumDebitByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void findByUserIdAndRawDescriptionContaining_shouldReturnTransactions_byKeyword() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT"),
            new Transaction(TEST_USER_ID, "Walmart Shopping", new BigDecimal("50.00"), TEST_DATE.plusDays(1), "DEBIT")
        );
        when(transactionRepository.findByUserIdAndRawDescriptionContaining(any(UUID.class), anyString()))
            .thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "Amazon");

        // Assert
        assertThat(result).hasSize(1);
        verify(transactionRepositoryMock)
            .findByUserIdAndRawDescriptionContaining(any(UUID.class), anyString());
    }

    @Test
    void findAll_shouldReturnPaginatedResults() {
        // Arrange
        Page<Transaction> page = new Page<>(List.of(), 0, 10);
        when(transactionRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        Page<Transaction> result = transactionRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 10));

        // Assert
        assertThat(result).hasSize(0);
        verify(transactionRepositoryMock).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void countByUserId_shouldReturnTotalCount() {
        // Arrange
        when(transactionRepository.countByUserId(any(UUID.class))).thenReturn(10L);

        // Act
        long result = transactionRepository.countByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(10L);
        verify(transactionRepositoryMock).countByUserId(any(UUID.class));
    }

    @Test
    void findByUserIdAndDateRange_shouldReturnEmpty_whenNoTransactionsInRange() {
        // Arrange
        when(transactionRepository.findByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void sumDebitByUserIdAndDateRange_shouldReturnZero_whenNoDebits() {
        // Arrange
        when(transactionRepository.sumDebitByUserIdAndDateRange(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.ZERO);

        // Act
        BigDecimal result = transactionRepository.sumDebitByUserIdAndDateRange(TEST_USER_ID, TEST_START_DATE, TEST_END_DATE);

        // Assert
        assertThat(result).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void findByUserIdAndRawDescriptionContaining_shouldReturnMultipleMatches() {
        // Arrange
        List<Transaction> transactions = List.of(
            new Transaction(TEST_USER_ID, "Amazon Purchase", TEST_AMOUNT_CENTS, TEST_DATE, "DEBIT"),
            new Transaction(TEST_USER_ID, "Walmart Shopping", new BigDecimal("50.00"), TEST_DATE.plusDays(1), "DEBIT")
        );
        when(transactionRepository.findByUserIdAndRawDescriptionContaining(any(UUID.class), anyString()))
            .thenReturn(transactions);

        // Act
        List<Transaction> result = transactionRepository.findByUserIdAndRawDescriptionContaining(TEST_USER_ID, "Store");

        // Assert
        assertThat(result).hasSize(2);
    }
}
