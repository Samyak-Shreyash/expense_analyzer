package com.expenseanalyzer.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * User preference entity with hybrid enum + JSONB storage.
 *
 * Typed preferences (theme, currency, notification mode) use enum fields for compile-time safety.
 * Dynamic preferences are stored in the value field as JSONB.
 */
@Getter
@Setter
@Entity
@Table(name = "preferences")
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "`key`", nullable = false, length = 100)
    private String key;

    // Enum fields for typed preferences (compile-time safety)
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 20)
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_mode", length = 20)
    private NotificationMode notificationMode;

    // JSONB field for dynamic preferences (flexibility)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "`value`", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> value = Map.of();

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
