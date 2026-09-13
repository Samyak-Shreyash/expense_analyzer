package com.expenseanalyzer.transaction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Transaction entity.
 */
class TransactionTest {

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_STATEMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_MERCHANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final String TEST_RAW_DESCRIPTION = "Amazon Purchase";
    private static final String TEST_NORMALIZED_DESCRIPTION = "Amazon.com - Electronics";
    private static final BigDecimal TEST_AMOUNT_CENTS = new BigDecimal("1234");
    private static final Instant TEST_DATE = Instant.parse("2026-09-01T12:00:00Z");
    private static final String TEST_TYPE = "DEBIT";

    @Test
    void constructor_shouldCreateTransaction_withDefaults() {
        // Arrange
        Transaction transaction = new Transaction();

        // Act
        Instant now = Instant.now();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);

        // Assert
        assertThat(transaction.getId()).isNull();
        assertThat(transaction.getRawDescription()).isNull();
        assertThat(transaction.getNormalizedDescription()).isNull();
        assertThat(transaction.getMerchantId()).isNull();
        assertThat(transaction.getCategoryId()).isNull();
        assertThat(transaction.getAmountCents()).isNull();
        assertThat(transaction.getDate()).isNull();
        assertThat(transaction.getType()).isNull();
        assertThat(transaction.getUserId()).isNull();
        assertThat(transaction.getStatementId()).isNull();
    }

    @Test
    void constructor_shouldCreateTransaction_withAllFields() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        transaction.setRawDescription(TEST_RAW_DESCRIPTION);
        transaction.setNormalizedDescription(TEST_NORMALIZED_DESCRIPTION);
        transaction.setMerchantId(TEST_MERCHANT_ID);
        transaction.setCategoryId(TEST_CATEGORY_ID);
        transaction.setAmountCents(TEST_AMOUNT_CENTS);
        transaction.setDate(TEST_DATE);
        transaction.setType(TEST_TYPE);
        transaction.setUserId(TEST_USER_ID);
        transaction.setStatementId(TEST_STATEMENT_ID);
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());

        // Assert
        assertThat(transaction.getId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(transaction.getRawDescription()).isEqualTo(TEST_RAW_DESCRIPTION);
        assertThat(transaction.getNormalizedDescription()).isEqualTo(TEST_NORMALIZED_DESCRIPTION);
        assertThat(transaction.getMerchantId()).isEqualTo(TEST_MERCHANT_ID);
        assertThat(transaction.getCategoryId()).isEqualTo(TEST_CATEGORY_ID);
        assertThat(transaction.getAmountCents()).isEqualTo(TEST_AMOUNT_CENTS);
        assertThat(transaction.getDate()).isEqualTo(TEST_DATE);
        assertThat(transaction.getType()).isEqualTo(TEST_TYPE);
        assertThat(transaction.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(transaction.getStatementId()).isEqualTo(TEST_STATEMENT_ID);
    }

    @Test
    void setRawDescription_shouldUpdateRawDescription() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setRawDescription(null);

        // Act
        transaction.setRawDescription(TEST_RAW_DESCRIPTION);

        // Assert
        assertThat(transaction.getRawDescription()).isEqualTo(TEST_RAW_DESCRIPTION);
    }

    @Test
    void setNormalizedDescription_shouldUpdateNormalizedDescription() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setNormalizedDescription(null);

        // Act
        transaction.setNormalizedDescription(TEST_NORMALIZED_DESCRIPTION);

        // Assert
        assertThat(transaction.getNormalizedDescription()).isEqualTo(TEST_NORMALIZED_DESCRIPTION);
    }

    @Test
    void setMerchantId_shouldUpdateMerchantId() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setMerchantId(null);

        // Act
        transaction.setMerchantId(TEST_MERCHANT_ID);

        // Assert
        assertThat(transaction.getMerchantId()).isEqualTo(TEST_MERCHANT_ID);
    }

    @Test
    void setCategoryId_shouldUpdateCategoryId() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setCategoryId(null);

        // Act
        transaction.setCategoryId(TEST_CATEGORY_ID);

        // Assert
        assertThat(transaction.getCategoryId()).isEqualTo(TEST_CATEGORY_ID);
    }

    @Test
    void setAmountCents_shouldUpdateAmountCents() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAmountCents(null);

        // Act
        transaction.setAmountCents(new BigDecimal("5000"));

        // Assert
        assertThat(transaction.getAmountCents()).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    void setDate_shouldUpdateDate() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setDate(null);
        Instant pastTime = Instant.parse("2026-01-01T00:00:00Z");

        // Act
        transaction.setDate(pastTime);

        // Assert
        assertThat(transaction.getDate()).isEqualTo(pastTime);
    }

    @Test
    void setType_shouldUpdateType() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType(null);
        String type = "DEBIT";

        // Act
        transaction.setType(type);

        // Assert
        assertThat(transaction.getType()).isEqualTo(type);
    }

    @Test
    void setUserId_shouldUpdateUserId() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setUserId(null);
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction.setUserId(testId);

        // Assert
        assertThat(transaction.getUserId()).isEqualTo(testId);
    }

    @Test
    void setStatementId_shouldUpdateStatementId() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setStatementId(null);
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        // Act
        transaction.setStatementId(testId);

        // Assert
        assertThat(transaction.getStatementId()).isEqualTo(testId);
    }

    @Test
    void getAmount_shouldReturnAmountInDollars() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAmountCents(new BigDecimal("1234"));

        // Act
        BigDecimal result = transaction.getAmount();

        // Assert
        assertThat(result).isEqualTo(new BigDecimal("12.34"));
    }

    @Test
    void getAmount_shouldReturnZero_whenNoAmount() {
        // Arrange
        Transaction transaction = new Transaction();

        // Act
        BigDecimal result = transaction.getAmount();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void formatAmount_shouldReturnFormattedString() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAmountCents(new BigDecimal("1234"));

        // Act
        String result = transaction.formatAmount();

        // Assert
        assertThat(result).isEqualTo("$12.34");
    }

    @Test
    void formatAmount_shouldReturnZero_whenNoAmount() {
        // Arrange
        Transaction transaction = new Transaction();

        // Act
        String result = transaction.formatAmount();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void isDebit_shouldReturnTrue_whenTypeIsDebit() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("DEBIT");

        // Act
        boolean result = transaction.isDebit();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isDebit_shouldReturnFalse_whenTypeIsCredit() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("CREDIT");

        // Act
        boolean result = transaction.isDebit();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isDebit_shouldReturnTrue_whenTypeIsUpper() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("debit");

        // Act
        boolean result = transaction.isDebit();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isDebit_shouldReturnFalse_whenTypeIsLower() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("credit");

        // Act
        boolean result = transaction.isDebit();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isCredit_shouldReturnTrue_whenTypeIsCredit() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("CREDIT");

        // Act
        boolean result = transaction.isCredit();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isCredit_shouldReturnFalse_whenTypeIsDebit() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("DEBIT");

        // Act
        boolean result = transaction.isCredit();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isCredit_shouldReturnTrue_whenTypeIsLower() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType("credit");

        // Act
        boolean result = transaction.isCredit();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void equals_shouldReturnTrue_whenSameTransaction() {
        // Arrange
        Transaction transaction1 = new Transaction();
        Transaction transaction2 = new Transaction();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction1.setId(testId);
        transaction2.setId(testId);

        boolean result = transaction1.equals(transaction2);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void equals_shouldReturnFalse_whenDifferentTransactions() {
        // Arrange
        Transaction transaction1 = new Transaction();
        Transaction transaction2 = new Transaction();
        UUID testId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID testId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        // Act
        transaction1.setId(testId1);
        transaction2.setId(testId2);

        boolean result = transaction1.equals(transaction2);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void equals_shouldReturnTrue_whenSameObject() {
        // Arrange
        Transaction transaction = new Transaction();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction.setId(testId);
        boolean result = transaction.equals(transaction);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void equals_shouldReturnFalse_whenNull() {
        // Arrange
        Transaction transaction = new Transaction();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction.setId(testId);
        boolean result = transaction.equals(null);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void equals_shouldReturnFalse_whenNotInstanceOfTransaction() {
        // Arrange
        Transaction transaction = new Transaction();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction.setId(testId);
        boolean result = transaction.equals(new Object());

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void hashCode_shouldReturnSameValue_whenSameTransaction() {
        // Arrange
        Transaction transaction1 = new Transaction();
        Transaction transaction2 = new Transaction();
        UUID testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // Act
        transaction1.setId(testId);
        transaction2.setId(testId);
        int hash1 = transaction1.hashCode();
        int hash2 = transaction2.hashCode();

        // Assert
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashCode_shouldReturnZero_whenNoId() {
        // Arrange
        Transaction transaction = new Transaction();

        // Act
        int result = transaction.hashCode();

        // Assert
        assertThat(result).isEqualTo(0);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsNull() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setRawDescription(null);

        // Act & Assert
        assertThatThrownBy(() -> transaction)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNull() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAmountCents(null);

        // Act & Assert
        assertThatThrownBy(() -> transaction)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenDateIsNull() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setDate(null);

        // Act & Assert
        assertThatThrownBy(() -> transaction)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenTypeIsNull() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setType(null);

        // Act & Assert
        assertThatThrownBy(() -> transaction)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenUserIdIsNull() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setUserId(null);

        // Act & Assert
        assertThatThrownBy(() -> transaction)
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("9999999999.99");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsTooLong() {
        // Arrange
        Transaction transaction = new Transaction();
        String longDescription = "A".repeat(2049);

        // Act & Assert
        assertThatThrownBy(() -> transaction.setRawDescription(longDescription))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("10000000000");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsTooLong() {
        // Arrange
        Transaction transaction = new Transaction();
        String longDescription = "A".repeat(2049);

        // Act & Assert
        assertThatThrownBy(() -> transaction.setRawDescription(longDescription))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("10000000000");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsTooLong() {
        // Arrange
        Transaction transaction = new Transaction();
        String longDescription = "A".repeat(2049);

        // Act & Assert
        assertThatThrownBy(() -> transaction.setRawDescription(longDescription))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("10000000000");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsTooLong() {
        // Arrange
        Transaction transaction = new Transaction();
        String longDescription = "A".repeat(2049);

        // Act & Assert
        assertThatThrownBy(() -> transaction.setRawDescription(longDescription))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("10000000000");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenRawDescriptionIsTooLong() {
        // Arrange
        Transaction transaction = new Transaction();
        String longDescription = "A".repeat(2049);

        // Act & Assert
        assertThatThrownBy(() -> transaction.setRawDescription(longDescription))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsTooLarge() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal largeAmount = new BigDecimal("10000000000");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(largeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void constructor_shouldThrow_whenAmountCentsIsNegative() {
        // Arrange
        Transaction transaction = new Transaction();
        BigDecimal negativeAmount = new BigDecimal("-100");

        // Act & Assert
        assertThatThrownBy(() -> transaction.setAmountCents(negativeAmount))
            .isInstanceOf(ConstraintViolationException.class);
    }
}
