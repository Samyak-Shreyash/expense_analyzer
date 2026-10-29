package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.MerchantSuggestionVote;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantSuggestionVoteRepository
        extends JpaRepository<MerchantSuggestionVote, MerchantSuggestionVote.Key> {

    /** Votes tallied for a (payee key, merchant): distinct voters and the earliest vote. */
    record Tally(String payeeKey, UUID merchantId, long voters, Instant first) {
    }

    /** Tallies for the given keys with at least two voters other than {@code userId}. One query for a whole page. */
    @Query("""
            SELECT new com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository$Tally(
                v.payeeKey, v.merchantId, COUNT(DISTINCT v.userId), MIN(v.createdAt))
            FROM MerchantSuggestionVote v
            WHERE v.payeeKey IN :keys AND v.userId <> :userId
            GROUP BY v.payeeKey, v.merchantId
            HAVING COUNT(DISTINCT v.userId) >= 2""")
    List<Tally> suggestions(Collection<String> keys, UUID userId);

    @Modifying
    @Query("DELETE FROM MerchantSuggestionVote v WHERE v.userId = :userId AND v.payeeKey = :payeeKey")
    void withdraw(UUID userId, String payeeKey);
}
