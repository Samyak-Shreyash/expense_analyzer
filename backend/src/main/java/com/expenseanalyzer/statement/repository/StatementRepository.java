package com.expenseanalyzer.statement.repository;

import com.expenseanalyzer.statement.model.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Statement entity.
 */
@Repository
public interface StatementRepository extends JpaRepository<Statement, UUID> {

    /**
     * Find all statements for a specific user.
     *
     * @param userId the user's UUID
     * @return list of statements
     */
    List<Statement> findByUserId(UUID userId);

    /**
     * Find a user's statement by account number (masked). Scoped by user: account numbers are not unique across users.
     *
     * @param userId the owner's UUID
     * @param accountNumber the masked account number
     * @return statement if found, null otherwise
     */
    Statement findByUserIdAndAccountNumber(UUID userId, String accountNumber);

    /** A user's statements, newest first. */
    List<Statement> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** A statement only if the user owns it. */
    Optional<Statement> findByIdAndUserId(UUID id, UUID userId);

    /** A user's statement with the given content hash, for duplicate detection. */
    Optional<Statement> findByUserIdAndSha256(UUID userId, String sha256);

    /**
     * Count statements for a user.
     *
     * @param userId the user's UUID
     * @return count of statements
     */
    long countByUserId(UUID userId);

    /**
     * Find all statements (for pagination).
     *
     * @param page page number
     * @param size page size
     * @return paginated list of statements
     */
    List<Statement> findAll();
}
