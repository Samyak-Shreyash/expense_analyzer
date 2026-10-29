package com.expenseanalyzer.audit.repository;

import com.expenseanalyzer.audit.model.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Write path is {@code AuditService}. No API exposes audit records. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
