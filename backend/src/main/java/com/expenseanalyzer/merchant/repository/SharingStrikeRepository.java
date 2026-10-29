package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.SharingStrike;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SharingStrikeRepository extends JpaRepository<SharingStrike, SharingStrike.Key> {

    /** Those of the given users with at least {@code min} strikes created after {@code since}. */
    @Query("SELECT s.userId FROM SharingStrike s WHERE s.userId IN :userIds AND s.createdAt > :since "
            + "GROUP BY s.userId HAVING COUNT(s) >= :min")
    List<UUID> withAtLeast(Collection<UUID> userIds, long min, Instant since);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    List<SharingStrike> findByUserId(UUID userId);
}
