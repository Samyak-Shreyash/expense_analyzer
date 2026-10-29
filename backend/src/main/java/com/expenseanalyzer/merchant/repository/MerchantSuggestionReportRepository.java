package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.MerchantSuggestionReport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantSuggestionReportRepository
        extends JpaRepository<MerchantSuggestionReport, MerchantSuggestionReport.Key> {

    /** Distinct reporters of the pair whose account was created at or before {@code joinedBefore}. */
    @Query("SELECT COUNT(DISTINCT r.userId) FROM MerchantSuggestionReport r, User u WHERE u.id = r.userId "
            + "AND r.payeeKey = :payeeKey AND r.merchantId = :merchantId AND u.createdAt <= :joinedBefore")
    long agedReporters(String payeeKey, UUID merchantId, Instant joinedBefore);

    /** When the pair was first reported (by anyone), or null. */
    @Query("SELECT MIN(r.createdAt) FROM MerchantSuggestionReport r "
            + "WHERE r.payeeKey = :payeeKey AND r.merchantId = :merchantId")
    Instant firstReportedAt(String payeeKey, UUID merchantId);

    /** Reports the user filed after {@code since}; for the per-user rate limit. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Locks the merchant row: serialises reports on all pairs of that merchant (works on Postgres and H2). */
    @Query(nativeQuery = true, value = "SELECT id FROM merchants WHERE id = :merchantId FOR UPDATE")
    List<Object> lockMerchant(UUID merchantId);

    /** Reports on the given payee keys (by anyone); only ever reduced to a reported/not-reported flag. */
    List<MerchantSuggestionReport> findByPayeeKeyIn(Collection<String> payeeKeys);
}
