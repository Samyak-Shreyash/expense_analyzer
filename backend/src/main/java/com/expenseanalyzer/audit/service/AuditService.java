package com.expenseanalyzer.audit.service;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.model.AuditLog;
import com.expenseanalyzer.audit.repository.AuditLogRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit events. Joins the caller's transaction, so an action that rolls back leaves no record;
 * outside a transaction (e.g. login) the record is written on its own.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(AuditAction action, UUID userId, String resourceType, UUID resourceId) {
        repository.save(new AuditLog(userId, action, resourceType, resourceId, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @Transactional
    public void record(AuditAction action, UUID userId) {
        record(action, userId, null, null);
    }
}
