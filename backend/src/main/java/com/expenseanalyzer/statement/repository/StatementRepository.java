package com.expenseanalyzer.statement.repository;

import com.expenseanalyzer.statement.model.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Statement entity.
 */
@Repository
public interface StatementRepository extends JpaRepository<Statement, Long> {

    /**
     * Find all statements for a specific user.
     *
     * @param userId the user's UUID
     * @return list of statements
     */
    List<Statement> findByUserId(UUID userId);

    /**
     * Find statement by account number (masked).
     *
     * @param accountNumber the masked account number
     * @return statement if found, null otherwise
     */
    Statement findByAccountNumber(String accountNumber);

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
