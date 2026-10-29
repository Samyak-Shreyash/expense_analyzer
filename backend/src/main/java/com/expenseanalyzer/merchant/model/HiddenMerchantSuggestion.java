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

/** A suggestion (payee key -> merchant) reported by at least two users; never shown to anyone again. */
@Entity
@Table(name = "hidden_merchant_suggestions")
@IdClass(HiddenMerchantSuggestion.Key.class)
@Getter
@NoArgsConstructor
public class HiddenMerchantSuggestion {

    public record Key(String payeeKey, UUID merchantId) implements Serializable {
    }

    @Id
    @Column(name = "payee_key", nullable = false, updatable = false, length = 255)
    private String payeeKey;

    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "hidden_at", nullable = false, updatable = false)
    private Instant hiddenAt;

    public HiddenMerchantSuggestion(String payeeKey, UUID merchantId) {
        this.payeeKey = payeeKey;
        this.merchantId = merchantId;
        this.hiddenAt = Instant.now();
    }
}
