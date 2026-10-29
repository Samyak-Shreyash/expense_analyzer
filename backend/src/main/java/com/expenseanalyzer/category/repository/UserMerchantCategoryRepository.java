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

    /** Drops the mapping rows for a category (the FK also cascades on Postgres; H2 tests have no FK). */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserMerchantCategory u WHERE u.categoryId = :categoryId")
    void deleteByCategoryId(UUID categoryId);
}
