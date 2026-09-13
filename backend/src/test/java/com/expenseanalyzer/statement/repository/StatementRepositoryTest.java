package com.expenseanalyzer.statement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.statement.model.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for StatementRepository.
 */
@ExtendWith(MockitoExtension.class)
class StatementRepositoryTest {

    @Mock
    private com.expenseanalyzer.statement.repository.StatementRepository statementRepositoryMock;

    @InjectMocks
    private StatementRepository statementRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_ACCOUNT_NUMBER = "**** **** 1234";
    private static final String TEST_MERCHANT_NAME = "Test Merchant";
    private static final String TEST_CATEGORY_NAME = "Food & Dining";

    @Test
    void findByUserId_shouldReturnStatements_whenUserExists() {
        // Arrange
        List<Statement> statements = List.of(
            new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A"),
            new Statement(TEST_USER_ID, "**** **** 5678", "Bank B")
        );
        when(statementRepository.findByUserId(any(UUID.class))).thenReturn(statements);

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        verify(statementRepositoryMock).findByUserId(any(UUID.class));
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenNoStatements() {
        // Arrange
        when(statementRepository.findByUserId(any(UUID.class))).thenReturn(List.of());

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verify(statementRepositoryMock).findByUserId(any(UUID.class));
    }

    @Test
    void findByAccountNumber_shouldReturnStatement_whenFound() {
        // Arrange
        Statement statement = new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A");
        when(statementRepository.findByAccountNumber(anyString())).thenReturn(statement);

        // Act
        Statement result = statementRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo(TEST_ACCOUNT_NUMBER);
        verify(statementRepositoryMock).findByAccountNumber(anyString());
    }

    @Test
    void findByAccountNumber_shouldReturnNull_whenNotFound() {
        // Arrange
        when(statementRepository.findByAccountNumber(anyString())).thenReturn(null);

        // Act
        Statement result = statementRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER);

        // Assert
        assertThat(result).isNull();
        verify(statementRepositoryMock).findByAccountNumber(anyString());
    }

    @Test
    void countByUserId_shouldReturnCount() {
        // Arrange
        when(statementRepository.countByUserId(any(UUID.class))).thenReturn(3L);

        // Act
        long result = statementRepository.countByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(3L);
        verify(statementRepositoryMock).countByUserId(any(UUID.class));
    }

    @Test
    void findAll_shouldReturnAllStatements() {
        // Arrange
        List<Statement> statements = List.of(
            new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A"),
            new Statement(TEST_USER_ID, "**** **** 5678", "Bank B")
        );
        when(statementRepository.findAll()).thenReturn(statements);

        // Act
        List<Statement> result = statementRepository.findAll();

        // Assert
        assertThat(result).hasSize(2);
        verify(statementRepositoryMock).findAll();
    }

    @Test
    void findByUserId_shouldReturnSingleStatement_whenOneExists() {
        // Arrange
        Statement statement = new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A");
        when(statementRepository.findByUserId(any(UUID.class))).thenReturn(List.of(statement));

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void findByAccountNumber_shouldReturnDifferentStatement_whenDifferentAccount() {
        // Arrange
        Statement statement = new Statement(TEST_USER_ID, "**** **** 9999", "Bank C");
        when(statementRepository.findByAccountNumber(anyString())).thenReturn(statement);

        // Act
        Statement result = statementRepository.findByAccountNumber("**** **** 9999");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo("**** **** 9999");
    }

    @Test
    void countByUserId_shouldReturnZero_whenNoStatements() {
        // Arrange
        when(statementRepository.countByUserId(any(UUID.class))).thenReturn(0L);

        // Act
        long result = statementRepository.countByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEqualTo(0L);
    }
}
