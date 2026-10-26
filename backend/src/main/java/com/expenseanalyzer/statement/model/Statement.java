package com.expenseanalyzer.statement.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Statement entity representing a bank statement/account.
 */
@Entity
@Table(name = "statements",
        uniqueConstraints = @UniqueConstraint(name = "uk_statements_user_sha256", columnNames = {"user_id", "sha256"}))
@Getter
@Setter
@NoArgsConstructor
public class Statement {

    /**
     * Primary key - UUID for distributed system compatibility.
     * Assigned on creation (not at persist time) so the storage object key, which embeds the id,
     * can be written in the same INSERT.
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    /**
     * Bank account number (masked for security).
     * Used as the primary identifier for the statement.
     */
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    /**
     * Bank name where the account is held.
     */
    @Column(name = "bank_name", length = 255)
    private String bankName;

    /**
     * Account type (CHECKING, SAVINGS, CREDIT_CARD).
     */
    @Column(name = "account_type", length = 50)
    private String accountType;

    /**
     * Currency code (ISO 4217).
     * Example: USD, INR, EUR.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Reference to the user who owns this statement/account.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Whether this statement is active/enabled.
     */
    @Column(name = "active", nullable = false, updatable = false)
    private boolean active;

    /**
     * Statement period start date (first transaction date).
     */
    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "period_start_date", nullable = true)
    private java.sql.Date periodStartDate;

    /**
     * Statement period end date (last transaction date).
     */
    @JdbcTypeCode(SqlTypes.DATE)
    @Column(name = "period_end_date", nullable = true)
    private java.sql.Date periodEndDate;

    /**
     * Timestamp when statement was created in the system.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when statement was last updated.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Filename the user sent; returned to the owner only, never logged or used as a storage key. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** Hex SHA-256 of the file content; unique per user for duplicate detection. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatementStatus status;

    /** Safe processing error code; never file contents. */
    @Column(name = "error_code", length = 64)
    private String errorCode;

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
     * Get the currency code for this statement.
     */
    public String getCurrencyCode() {
        return currencyCode != null ? currencyCode.toUpperCase() : "USD";
    }

    // ---- Constructor with parameters for testing ----

    /**
     * Constructor for creating a Statement with all fields.
     */
    public Statement(UUID userId, String accountNumber, String bankName) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.bankName = bankName;
        this.currencyCode = "USD";
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ---- Equality based on UUID ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Statement statement)) return false;
        return id != null && id.equals(statement.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
