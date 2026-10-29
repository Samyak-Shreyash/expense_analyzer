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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for StatementRepository.
 */
@ExtendWith(MockitoExtension.class)
class StatementRepositoryTest {

    @Mock
    private StatementRepository statementRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_ACCOUNT_NUMBER = "**** **** 1234";

    @Test
    void findByUserId_shouldReturnStatements_whenUserExists() {
        // Arrange
        List<Statement> statements = List.of(
            new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A"),
            new Statement(TEST_USER_ID, "**** **** 5678", "Bank B")
        );
        when(statementRepository.findByUserId(TEST_USER_ID)).thenReturn(statements);

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenNoStatements() {
        // Arrange
        when(statementRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of());

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
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
    }

    @Test
    void findByUserId_shouldReturnSingleStatement_whenOneExists() {
        // Arrange
        Statement statement = new Statement(TEST_USER_ID, TEST_ACCOUNT_NUMBER, "Bank A");
        when(statementRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of(statement));

        // Act
        List<Statement> result = statementRepository.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(1);
    }

}
