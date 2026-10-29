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
     * before {@code joinedBefore}. One query for a whole page.
     */
    @Query("""
            SELECT new com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository$Tally(
                v.payeeKey, v.merchantId, COUNT(DISTINCT v.userId))
            FROM MerchantSuggestionVote v, User u
            WHERE u.id = v.userId AND v.payeeKey IN :keys AND v.userId <> :userId AND u.createdAt <= :joinedBefore
            GROUP BY v.payeeKey, v.merchantId""")
    List<Tally> tallies(Collection<String> keys, UUID userId, Instant joinedBefore);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE MerchantSuggestionVote v SET v.merchantId = :merchantId, v.createdAt = :now "
            + "WHERE v.userId = :userId AND v.payeeKey = :payeeKey")
    int repoint(UUID userId, String payeeKey, UUID merchantId, Instant now);

    /** Locks the voter's user row so one user's concurrent vote replacements run one at a time. */
    @Query(nativeQuery = true, value = "SELECT id FROM users WHERE id = :userId FOR UPDATE")
    List<Object> lockVoter(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MerchantSuggestionVote v WHERE v.userId = :userId AND v.payeeKey IN :payeeKeys")
    void withdraw(UUID userId, Collection<String> payeeKeys);
}
