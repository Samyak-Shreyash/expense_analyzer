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

/**
 * The user's own categories for a merchant: one for money out, one for money in. At most one row per (user, merchant);
 * clearing both deletes the row.
 */
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

    /** Money out. */
    @Column(name = "category_id")
    private UUID categoryId;

    /** Money in. */
    @Column(name = "credit_category_id")
    private UUID creditCategoryId;

    public UserMerchantCategory(UUID userId, UUID merchantId) {
        this.userId = userId;
        this.merchantId = merchantId;
    }

    /** A mapping with only a money-out category. */
    public UserMerchantCategory(UUID userId, UUID merchantId, UUID categoryId) {
        this(userId, merchantId);
        this.categoryId = categoryId;
    }

    /** The category for money in ({@code credit}) or out. */
    public UUID categoryFor(boolean credit) {
        return credit ? creditCategoryId : categoryId;
    }

    public void setCategoryFor(boolean credit, UUID id) {
        if (credit) {
            creditCategoryId = id;
        } else {
            categoryId = id;
        }
    }
}
