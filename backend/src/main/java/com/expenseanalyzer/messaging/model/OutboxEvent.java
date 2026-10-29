package com.expenseanalyzer.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A domain event written in the same DB transaction as the state change, published to Kafka afterwards. */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    /** Also the envelope's eventId, so consumers can de-duplicate on it. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Full JSON envelope (EVENT-MODEL section 2), sent to Kafka unchanged. */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Exception class name of the last failed send; never a message that could carry data. */
    @Column(name = "last_error", length = 255)
    private String lastError;
}
