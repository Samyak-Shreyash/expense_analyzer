package com.expenseanalyzer.processing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Records that a consumer finished an event, so redelivery is a no-op. */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @EmbeddedId
    private Key id;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId, String consumer) {
        this.id = new Key(eventId, consumer);
        this.processedAt = Instant.now();
    }

    @Embeddable
    public record Key(
            @Column(name = "event_id", nullable = false) UUID eventId,
            @Column(name = "consumer", nullable = false, length = 100) String consumer) implements Serializable {
    }
}
