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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        uniqueConstraints = @UniqueConstraint(name = "uq_statements_user_file_hash", columnNames = {"user_id", "file_hash"}))
@Getter
@Setter
@NoArgsConstructor
public class Statement {

    /**
     * Primary key - UUID for distributed system compatibility.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

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
    @Column(name = "currency_code", length = 3)
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

    /**
     * Original upload file name, shown back to the owner. Never logged.
     */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /**
     * Object-storage key of the original file.
     */
    @Column(name = "storage_key", length = 512)
    private String storageKey;

    /**
     * SHA-256 (hex) of the uploaded file; unique per user.
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 10)
    private StatementFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatementStatus status = StatementStatus.UPLOADED;

    /**
     * Safe, user-facing error code when processing failed. Never contains file contents.
     */
    @Column(name = "processing_error", length = 100)
    private String processingError;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "completed_at")
    private Instant completedAt;

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
