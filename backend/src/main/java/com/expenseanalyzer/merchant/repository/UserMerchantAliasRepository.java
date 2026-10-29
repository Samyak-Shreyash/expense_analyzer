package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMerchantAliasRepository extends JpaRepository<UserMerchantAlias, UserMerchantAlias.Key> {

    List<UserMerchantAlias> findByUserId(UUID userId);

    Optional<UserMerchantAlias> findByUserIdAndPublicId(UUID userId, UUID publicId);

    /** What sharing eligibility needs from one of the user's transactions. */
    record PayeeRow(String rawDescription, String normalizedDescription, UUID personId) {
    }

    /** All of the user's transactions, reduced to {@link PayeeRow}, in one query. */
    @Query("SELECT new com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository$PayeeRow("
            + "t.rawDescription, t.normalizedDescription, t.personId) FROM Transaction t WHERE t.userId = :userId")
    List<PayeeRow> payeeRows(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserMerchantAlias a WHERE a.userId = :userId AND a.payeeKey IN :payeeKeys")
    void deleteByUserIdAndPayeeKeys(UUID userId, Collection<String> payeeKeys);
}
