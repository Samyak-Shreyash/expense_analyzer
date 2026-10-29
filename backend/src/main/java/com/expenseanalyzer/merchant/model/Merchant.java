package com.expenseanalyzer.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A canonical merchant shared across users (e.g. "Swiggy"). Holds no user data. */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "canonical_name", nullable = false, unique = true, length = 255)
    private String canonicalName;

    @Column(name = "parent_merchant_id")
    private UUID parentMerchantId;

    @Column(name = "category_hint", length = 50)
    private String categoryHint;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Merchant(String canonicalName, String categoryHint) {
        this.canonicalName = canonicalName;
        this.categoryHint = categoryHint;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
