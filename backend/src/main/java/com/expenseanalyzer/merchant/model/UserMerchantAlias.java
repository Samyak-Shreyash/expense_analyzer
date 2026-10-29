package com.expenseanalyzer.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The user's own merchant for a payee key (see PayeeKey). At most one row per (user, payee). */
@Entity
@Table(name = "user_merchant_aliases")
@IdClass(UserMerchantAlias.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class UserMerchantAlias {

    public record Key(UUID userId, String payeeKey) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "payee_key", nullable = false, updatable = false, length = 255)
    private String payeeKey;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** Exactly what the user typed; shown to them instead of the shared merchant's canonical spelling. */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserMerchantAlias(UUID userId, String payeeKey, UUID merchantId, String displayName) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.merchantId = merchantId;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }
}
