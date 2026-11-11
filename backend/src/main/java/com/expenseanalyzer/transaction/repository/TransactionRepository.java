package com.expenseanalyzer.transaction.repository;

import com.expenseanalyzer.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for Transaction entity.
 *
 * Acceptance Criteria:
 * - Repository with methods for querying by user and statement
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /**
     * Find all transactions for a specific user.
     *
     * @param userId the user's UUID
     * @return list of transactions
     */
    List<Transaction> findByUserId(UUID userId);

    /**
     * Find a user's transactions for a specific statement (bank account).
     *
     * @param userId the owner's UUID
     * @param statementId the statement's UUID
     * @return list of transactions
     */
    List<Transaction> findByUserIdAndStatementId(UUID userId, UUID statementId);

    /**
     * Find all transactions (for pagination).
     *
     * @param pageable the pageable parameter
     * @return paginated list of transactions
     */
    Page<Transaction> findAll(Pageable pageable);

    /** Detaches the category from every transaction (the FK also does this on Postgres; H2 tests have no FK). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.categoryId = NULL WHERE t.categoryId = :categoryId")
    void clearCategory(UUID categoryId);

    /** Moves all of the user's transactions with one person to another (merging people). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.personId = :to WHERE t.userId = :userId AND t.personId = :from")
    void reassignPerson(UUID userId, UUID from, UUID to);

    List<Transaction> findByUserIdAndPersonId(UUID userId, UUID personId);

    boolean existsByUserIdAndPersonId(UUID userId, UUID personId);

    /** Removes a statement's transactions before the statement itself is deleted. */
    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.statementId = :statementId")
    void deleteByStatementId(UUID statementId);

    /** The user's transactions sharing a normalized description (counterparty corrections apply to all of them). */
    List<Transaction> findByUserIdAndNormalizedDescription(UUID userId, String normalizedDescription);

    /** Which of these fingerprints the user already has. */
    @Query("SELECT t.transactionFingerprint FROM Transaction t WHERE t.userId = :userId AND t.transactionFingerprint IN :fingerprints")
    Set<String> findExistingFingerprints(UUID userId, Collection<String> fingerprints);

    /** The user's rows open to automatic transfer matching: not a transfer or duplicate, never marked or unmarked by them. */
    List<Transaction> findByUserIdAndTransferFalseAndTransferManualFalseAndDuplicateFalse(UUID userId);

    /** Rows: [type, sum of amountCents] over own-account transfers only. */
    @Query("SELECT t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.transfer = TRUE AND t.duplicate = FALSE AND t.date >= :from AND t.date < :to GROUP BY t.type")
    List<Object[]> sumTransfersByDirection(UUID userId, Instant from, Instant to);

    /** The user's rows carrying any of these UPI references (a bank row and a UPI app row share one). */
    List<Transaction> findByUserIdAndUpiRefIn(UUID userId, Collection<String> upiRefs);

    /** Ownership-scoped lookup: another user's transaction is indistinguishable from a missing one. */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    // ---- Aggregations for analytics. Range is [from, to). Rows are Object[]; see each method. A refund pair counts only
    // for the charges on its payment. ----

    /** Rows: [type (String), sum of amountCents (BigDecimal), count (Long)]. */
    @Query("SELECT t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END), COUNT(t) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.transfer = FALSE AND t.duplicate = FALSE AND (t.reversalPairId IS NULL OR t.reversalChargePaise IS NOT NULL) AND t.date >= :from AND t.date < :to GROUP BY t.type")
    List<Object[]> sumByDirection(UUID userId, Instant from, Instant to);

    /** Rows: [merchantId (UUID or null), type, sum of amountCents, count]. */
    @Query("SELECT t.merchantId, t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END), COUNT(t) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.transfer = FALSE AND t.duplicate = FALSE AND (t.reversalPairId IS NULL OR t.reversalChargePaise IS NOT NULL) AND t.date >= :from AND t.date < :to GROUP BY t.merchantId, t.type")
    List<Object[]> sumByMerchantAndDirection(UUID userId, Instant from, Instant to);

    /** Rows: [year (Integer), month (Integer), type, sum of amountCents]. Dates are UTC days. */
    @Query("SELECT YEAR(t.date), MONTH(t.date), t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.transfer = FALSE AND t.duplicate = FALSE AND (t.reversalPairId IS NULL OR t.reversalChargePaise IS NOT NULL) AND t.date >= :from AND t.date < :to "
            + "GROUP BY YEAR(t.date), MONTH(t.date), t.type")
    List<Object[]> sumByMonthAndDirection(UUID userId, Instant from, Instant to);

    /** Rows: [categoryId, personId, merchantId (each UUID or null), type, count] over all the user's transactions. */
    @Query("SELECT t.categoryId, t.personId, t.merchantId, t.type, COUNT(t) FROM Transaction t WHERE t.userId = :userId "
            + "GROUP BY t.categoryId, t.personId, t.merchantId, t.type")
    List<Object[]> countByCategoryGroup(UUID userId);

    /** Rows: [categoryId, personId, merchantId (each UUID or null), type, sum of amountCents, count]. */
    @Query("SELECT t.categoryId, t.personId, t.merchantId, t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END), COUNT(t) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.transfer = FALSE AND t.duplicate = FALSE AND (t.reversalPairId IS NULL OR t.reversalChargePaise IS NOT NULL) AND t.date >= :from AND t.date < :to "
            + "GROUP BY t.categoryId, t.personId, t.merchantId, t.type")
    List<Object[]> sumByCategoryGroup(UUID userId, Instant from, Instant to);

    /** Rows: [year, month, categoryId, personId, merchantId, type, sum of amountCents]. Dates are UTC days. */
    @Query("SELECT YEAR(t.date), MONTH(t.date), t.categoryId, t.personId, t.merchantId, t.type, SUM(CASE WHEN t.reversalPairId IS NULL THEN t.amountCents ELSE t.reversalChargePaise END) "
            + "FROM Transaction t WHERE t.userId = :userId AND t.transfer = FALSE AND t.duplicate = FALSE AND (t.reversalPairId IS NULL OR t.reversalChargePaise IS NOT NULL) AND t.date >= :from AND t.date < :to "
            + "GROUP BY YEAR(t.date), MONTH(t.date), t.categoryId, t.personId, t.merchantId, t.type")
    List<Object[]> sumByMonthAndCategoryGroup(UUID userId, Instant from, Instant to);
}
