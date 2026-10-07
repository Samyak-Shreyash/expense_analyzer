package com.expenseanalyzer.user.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Email
    @NotBlank
    @Size(max = 255)
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash of the user's password.
     * NEVER store or log the raw password.
     */
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Size(max = 255)
    @Column(name = "full_name", length = 255)
    private String fullName;

    /**
     * Optional user preferences stored as JSONB.
     * Example: {"currency":"INR","theme":"dark","notifications":true}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Preference> preferences = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ---- Lifecycle hooks ----

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ---- Business helpers ----

    /**
     * Mark this user as having just logged in.
     */
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    /**
     * Used by Spring Security to check if the account is enabled.
     */
    public boolean isEnabled() {
        return active;
    }

    // ---- Equality based on UUID ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    // ---- Constructors ----

    /**
     * Constructor for creating a new user.
     */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = "";
        this.preferences = new HashMap<>();
        this.active = true;
        this.role = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.lastLoginAt = null;
    }

    /**
     * Constructor for creating a new user with preferences.
     */
    public User(String email, String passwordHash, Map<String, Preference> preferences) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = "";
        this.preferences = preferences != null ? preferences : new HashMap<>();
        this.active = true;
        this.role = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.lastLoginAt = null;
    }
}
