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

/** A user's opt-in vote that a payee key is a merchant. At most one per (user, payee); never exposed. */
@Entity
@Table(name = "merchant_suggestion_votes")
@IdClass(MerchantSuggestionVote.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class MerchantSuggestionVote {

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

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MerchantSuggestionVote(UUID userId, String payeeKey, UUID merchantId) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.merchantId = merchantId;
        this.createdAt = Instant.now();
    }
}
