package com.expenseanalyzer.category.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The user's own category for a merchant. At most one row per (user, merchant); clearing deletes the row. */
@Entity
@Table(name = "user_merchant_categories")
@IdClass(UserMerchantCategory.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class UserMerchantCategory {

    public record Key(UUID userId, UUID merchantId) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    public UserMerchantCategory(UUID userId, UUID merchantId, UUID categoryId) {
        this.userId = userId;
        this.merchantId = merchantId;
        this.categoryId = categoryId;
    }
}
