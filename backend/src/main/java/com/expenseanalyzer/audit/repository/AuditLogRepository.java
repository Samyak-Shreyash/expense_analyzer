package com.expenseanalyzer.audit.repository;

import com.expenseanalyzer.audit.model.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Write path is {@code AuditService}. No API exposes audit records. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Native: AuditLog is @Immutable, which Hibernate rejects in bulk JPQL deletes.
    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE occurred_at <= :cutoff", nativeQuery = true)
    int deleteOccurredAtOrBefore(Instant cutoff);

    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE user_id IS NULL AND occurred_at <= :cutoff", nativeQuery = true)
    int deleteWithoutUserOccurredAtOrBefore(Instant cutoff);
}
