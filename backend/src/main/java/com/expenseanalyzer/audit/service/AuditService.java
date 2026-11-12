package com.expenseanalyzer.audit.service;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.model.AuditLog;
import com.expenseanalyzer.audit.repository.AuditLogRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit events. Joins the caller's transaction, so an action that rolls back leaves no record;
 * outside a transaction (e.g. login) the record is written on its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /** Retention per docs/architecture/ADR/SECURITY-REQUIREMENTS.md section 33.1. */
    @Value("${app.audit-purge.retention:90d}")
    private Duration retention;

    /** Shorter retention for rows without a user: deleted accounts and unknown-email logins. */
    @Value("${app.audit-purge.retention-without-user:30d}")
    private Duration retentionWithoutUser;

    @Transactional
    public void record(AuditAction action, UUID userId, String resourceType, UUID resourceId) {
        repository.save(new AuditLog(userId, action, resourceType, resourceId, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @Transactional
    public void record(AuditAction action, UUID userId) {
        record(action, userId, null, null);
    }

    /** Deletes rows at or past retention. Logs counts only. */
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();
        int expired = repository.deleteOccurredAtOrBefore(now.minus(retention));
        int withoutUser = repository.deleteWithoutUserOccurredAtOrBefore(now.minus(retentionWithoutUser));
        if (expired + withoutUser > 0) {
            log.info("Audit purge removed rows: expired={}, withoutUser={}", expired, withoutUser);
        }
    }
}
