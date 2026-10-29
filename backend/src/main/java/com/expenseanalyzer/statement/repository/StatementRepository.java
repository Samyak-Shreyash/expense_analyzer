package com.expenseanalyzer.statement.repository;

import com.expenseanalyzer.statement.model.Statement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Ownership-scoped lookup: another user's statement is indistinguishable from a missing one. */
    Optional<Statement> findByIdAndUserId(UUID id, UUID userId);

    Optional<Statement> findByUserIdAndFileHash(UUID userId, String fileHash);

    Page<Statement> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
