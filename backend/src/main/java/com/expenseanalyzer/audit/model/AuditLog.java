package com.expenseanalyzer.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One audit record. Immutable once written; carries ids and codes only, never personal data. */
@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@ToString
@NoArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Acting user; null when unknown (e.g. login attempt for an unregistered email). */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Column(name = "resource_type", updatable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public AuditLog(UUID userId, AuditAction action, String resourceType, UUID resourceId, String correlationId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }
}
