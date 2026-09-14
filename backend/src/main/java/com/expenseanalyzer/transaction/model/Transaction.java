package com.expenseanalyzer.transaction.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transaction entity representing a financial transaction.
 *
 * Acceptance Criteria:
 * - Transaction entity with all fields (raw_description, normalized_description, merchant_id, category_id, amount, date)
 * - Spring Data JPA repository with methods for querying by user and statement
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    /**
     * Primary key - UUID for distributed system compatibility.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Raw description from source (e.g., bank statement).
     * Preserved as-is for audit purposes.
     */
    @Column(name = "raw_description", length = 2048, nullable = false)
    private String rawDescription;

    /**
     * Normalized/processed description after enrichment.
     * Contains structured data like merchant name, category, etc.
     */
    @Column(name = "normalized_description", length = 1024, nullable = true)
    private String normalizedDescription;

    /**
     * Merchant identifier (from external provider).
     * Used for matching transactions to known merchants.
     */
    @Column(name = "merchant_id", nullable = true)
    private UUID merchantId;

    /**
     * Category identifier (from expense analyzer categories).
     * Used for transaction categorization and reporting.
     */
    @Column(name = "category_id", nullable = true)
    private UUID categoryId;

    /**
     * Transaction amount in cents (to avoid floating point issues).
     * Using BigDecimal for financial accuracy.
     */
    @DecimalMax(value = "9999999999.99")
    @Column(name = "amount_cents", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountCents;

    /**
     * Transaction date (transaction occurred on).
     * Used for chronological queries and period-based aggregations.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "date", nullable = false, updatable = false)
    private Instant date;

    /**
     * Transaction type (DEBIT/CREDIT).
     * DEBIT: money leaving the account
     * CREDIT: money entering the account
     */
    @Column(name = "type", nullable = false, length = 10)
    private String type;

    /**
     * Reference to the user who owns this transaction.
     * Used for filtering transactions by owner.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Reference to the bank statement (account) this transaction belongs to.
     * Used for grouping transactions by account/statement.
     */
    @Column(name = "statement_id", nullable = true)
    private UUID statementId;

    /**
     * Timestamp when transaction was created in the system.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when transaction was last updated.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---- Lifecycle hooks ----

    /**
     * Create timestamp - set at persistence time.
     */
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Update timestamp - set on modification.
     */
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ---- Business helpers ----

    /**
     * Convert amount from cents to BigDecimal (dollars).
     */
    public BigDecimal getAmount() {
        if (amountCents == null) {
            return null;
        }
        return amountCents.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Format amount as currency string.
     */
    public String formatAmount() {
        BigDecimal amount = getAmount();
        if (amount == null) {
            return null;
        }
        return String.format("$%.2f", amount.doubleValue());
    }

    /**
     * Check if this is a debit transaction (money leaving account).
     */
    public boolean isDebit() {
        return "DEBIT".equalsIgnoreCase(type);
    }

    /**
     * Check if this is a credit transaction (money entering account).
     */
    public boolean isCredit() {
        return "CREDIT".equalsIgnoreCase(type);
    }

    // ---- Constructor with parameters for testing ----

    /**
     * Constructor for creating a Transaction with all fields.
     */
    public Transaction(UUID userId, String rawDescription, BigDecimal amountCents, Instant date, String type) {
        this.rawDescription = rawDescription;
        this.amountCents = amountCents;
        this.date = date;
        this.type = type;
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ---- Equality based on UUID ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction transaction)) return false;
        return id != null && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
