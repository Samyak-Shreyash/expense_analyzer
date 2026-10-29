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
import java.time.LocalDate;
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
     * Find transactions within a date range for a user.
     *
     * @param userId the user's UUID
     * @param startDate inclusive start date
     * @param endDate inclusive end date
     * @return list of transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :startDate AND :endDate")
    List<Transaction> findByUserIdAndDateRange(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Count transactions for a user within a date range.
     *
     * @param userId the user's UUID
     * @param startDate inclusive start date
     * @param endDate inclusive end date
     * @return count of transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :startDate AND :endDate")
    long countByUserIdAndDateRange(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Find debit transactions for a user within a date range.
     *
     * @param userId the user's UUID
     * @param startDate inclusive start date
     * @param endDate inclusive end date
     * @return list of debit transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :startDate AND :endDate AND t.type = 'DEBIT'")
    List<Transaction> findByUserIdAndDateRangeAndType(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by merchant ID for a user.
     *
     * @param userId the user's UUID
     * @param merchantId the merchant's UUID
     * @return list of transactions
     */
    List<Transaction> findByUserIdAndMerchantId(UUID userId, UUID merchantId);

    /**
     * Find transactions by category ID for a user.
     *
     * @param userId the user's UUID
     * @param categoryId the category's UUID
     * @return list of transactions
     */
    List<Transaction> findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    /**
     * Count total debit transactions for a user.
     *
     * @param userId the user's UUID
     * @return count of debit transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.type = 'DEBIT'")
    long countDebitByUserId(UUID userId);

    /**
     * Find total amount spent (debit) by a user within a date range.
     *
     * @param userId the user's UUID
     * @param startDate inclusive start date
     * @param endDate inclusive end date
     * @return total amount in cents
     */
    @Query("SELECT SUM(t.amountCents) FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :startDate AND :endDate AND t.type = 'DEBIT'")
    BigDecimal sumDebitByUserIdAndDateRange(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Find transactions by raw description keyword for a user.
     *
     * @param userId the user's UUID
     * @param keyword search keyword
     * @return list of matching transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND LOWER(t.rawDescription) LIKE LOWER(:keyword)")
    List<Transaction> findByUserIdAndRawDescriptionContaining(UUID userId, String keyword);

    /**
     * Find all transactions (for pagination).
     *
     * @param pageable the pageable parameter
     * @return paginated list of transactions
     */
    Page<Transaction> findAll(Pageable pageable);

    /**
     * Count total transactions for a user.
     *
     * @param userId the user's UUID
     * @return count of all transactions
     */
    long countByUserId(UUID userId);

    /** Removes a statement's transactions before the statement itself is deleted. */
    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.statementId = :statementId")
    void deleteByStatementId(UUID statementId);

    /** Which of these fingerprints the user already has. */
    @Query("SELECT t.transactionFingerprint FROM Transaction t WHERE t.userId = :userId AND t.transactionFingerprint IN :fingerprints")
    Set<String> findExistingFingerprints(UUID userId, Collection<String> fingerprints);

    /** Ownership-scoped lookup: another user's transaction is indistinguishable from a missing one. */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    // ---- Aggregations for analytics. Range is [from, to). Rows are Object[]; see each method. ----

    /** Rows: [type (String), sum of amountCents (BigDecimal), count (Long)]. */
    @Query("SELECT t.type, SUM(t.amountCents), COUNT(t) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.date >= :from AND t.date < :to GROUP BY t.type")
    List<Object[]> sumByDirection(UUID userId, Instant from, Instant to);

    /** Rows: [merchantId (UUID or null), type, sum of amountCents, count]. */
    @Query("SELECT t.merchantId, t.type, SUM(t.amountCents), COUNT(t) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.date >= :from AND t.date < :to GROUP BY t.merchantId, t.type")
    List<Object[]> sumByMerchantAndDirection(UUID userId, Instant from, Instant to);

    /** Rows: [year (Integer), month (Integer), type, sum of amountCents]. Dates are UTC days. */
    @Query("SELECT YEAR(t.date), MONTH(t.date), t.type, SUM(t.amountCents) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.date >= :from AND t.date < :to "
            + "GROUP BY YEAR(t.date), MONTH(t.date), t.type")
    List<Object[]> sumByMonthAndDirection(UUID userId, Instant from, Instant to);
}
