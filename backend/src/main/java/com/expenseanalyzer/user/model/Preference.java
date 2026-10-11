package com.expenseanalyzer.user.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * User preference entity with hybrid enum + JSONB storage.
 *
 * Typed preferences (theme, currency, notification mode) use enum fields for compile-time safety.
 * Dynamic preferences are stored in the value field as JSONB.
 */
@Entity
@Table(name = "preferences")
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "key", nullable = false, length = 100)
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
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> value;

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

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public NotificationMode getNotificationMode() {
        return notificationMode;
    }

    public void setNotificationMode(NotificationMode notificationMode) {
        this.notificationMode = notificationMode;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    public void setValue(Map<String, Object> value) {
        this.value = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
