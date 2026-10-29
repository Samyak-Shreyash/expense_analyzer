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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A strike against a user who had voted for a suggestion that was hidden after reports. One per pair. */
@Entity
@Table(name = "sharing_strikes")
@IdClass(SharingStrike.Key.class)
@Getter
@NoArgsConstructor
public class SharingStrike {

    public record Key(UUID userId, String payeeKey, UUID merchantId) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "payee_key", nullable = false, updatable = false, length = 255)
    private String payeeKey;

    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SharingStrike(UUID userId, String payeeKey, UUID merchantId) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.merchantId = merchantId;
        this.createdAt = Instant.now();
    }
}
