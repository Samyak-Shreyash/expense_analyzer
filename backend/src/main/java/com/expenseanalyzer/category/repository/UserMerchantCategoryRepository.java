package com.expenseanalyzer.category.repository;

import com.expenseanalyzer.category.model.UserMerchantCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMerchantCategoryRepository extends JpaRepository<UserMerchantCategory, UserMerchantCategory.Key> {

    List<UserMerchantCategory> findByUserId(UUID userId);

    Optional<UserMerchantCategory> findByUserIdAndMerchantId(UUID userId, UUID merchantId);

    /** Clears a category from the money-out side of every mapping (the FK does this on Postgres; H2 has no FK). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserMerchantCategory u SET u.categoryId = NULL WHERE u.categoryId = :categoryId")
    void clearCategory(UUID categoryId);

    /** Clears a category from the money-in side of every mapping. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserMerchantCategory u SET u.creditCategoryId = NULL WHERE u.creditCategoryId = :categoryId")
    void clearCreditCategory(UUID categoryId);

    /** Drops mappings with neither category left. */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserMerchantCategory u WHERE u.categoryId IS NULL AND u.creditCategoryId IS NULL")
    void deleteEmpty();
}
