package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMerchantAliasRepository extends JpaRepository<UserMerchantAlias, UserMerchantAlias.Key> {

    List<UserMerchantAlias> findByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserMerchantAlias a WHERE a.userId = :userId AND a.payeeKey IN :payeeKeys")
    void deleteByUserIdAndPayeeKeys(UUID userId, Collection<String> payeeKeys);
}
