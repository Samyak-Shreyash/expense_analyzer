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

    /** Distinct voters for a (payee key, merchant). */
    record Tally(String payeeKey, UUID merchantId, long voters) {
    }

    /**
     * Tallies for the given keys, counting only voters other than {@code userId} whose account was created at or
     * before {@code joinedBefore}. Pairs hidden after reports, and pairs the caller reported, are left out. One query
     * for a whole page.
     */
    @Query("""
            SELECT new com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository$Tally(
                v.payeeKey, v.merchantId, COUNT(DISTINCT v.userId))
            FROM MerchantSuggestionVote v, User u
            WHERE u.id = v.userId AND v.payeeKey IN :keys AND v.userId <> :userId AND u.createdAt <= :joinedBefore
              AND NOT EXISTS (SELECT 1 FROM HiddenMerchantSuggestion h
                              WHERE h.payeeKey = v.payeeKey AND h.merchantId = v.merchantId)
              AND NOT EXISTS (SELECT 1 FROM MerchantSuggestionReport r
                              WHERE r.userId = :userId AND r.payeeKey = v.payeeKey AND r.merchantId = v.merchantId)
            GROUP BY v.payeeKey, v.merchantId""")
    List<Tally> tallies(Collection<String> keys, UUID userId, Instant joinedBefore);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE MerchantSuggestionVote v SET v.merchantId = :merchantId, v.createdAt = :now "
            + "WHERE v.userId = :userId AND v.payeeKey = :payeeKey")
    int repoint(UUID userId, String payeeKey, UUID merchantId, Instant now);

    /** Locks the voter's user row so one user's concurrent vote replacements run one at a time. */
    @Query(nativeQuery = true, value = "SELECT id FROM users WHERE id = :userId FOR NO KEY UPDATE")
    List<Object> lockVoter(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MerchantSuggestionVote v WHERE v.userId = :userId AND v.payeeKey IN :payeeKeys")
    void withdraw(UUID userId, Collection<String> payeeKeys);

    List<MerchantSuggestionVote> findByUserId(UUID userId);

    @Query("SELECT v.userId FROM MerchantSuggestionVote v WHERE v.payeeKey = :payeeKey AND v.merchantId = :merchantId")
    List<UUID> voters(String payeeKey, UUID merchantId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MerchantSuggestionVote v WHERE v.userId IN :userIds")
    void withdrawAll(Collection<UUID> userIds);
}
