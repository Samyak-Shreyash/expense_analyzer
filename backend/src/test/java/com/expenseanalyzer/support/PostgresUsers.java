package com.expenseanalyzer.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Inserts the users row that tests with a mocked UserRepository never write. H2 runs without
 * foreign keys and doesn't need it; Postgres rejects statements and transactions without it.
 */
public final class PostgresUsers {

    private PostgresUsers() {
    }

    /** The email is derived from the id: the mocked repository resolves the real one, and it must stay unique. */
    public static void insert(JdbcTemplate jdbc, UUID id) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id, email, password_hash, role, is_active, categories_seeded, "
                        + "share_merchant_names, sharing_revoked, created_at, updated_at) "
                        + "VALUES (?, ?, 'x', 'USER', TRUE, TRUE, FALSE, FALSE, ?, ?)",
                id, id + "@postgres.test", now, now);
    }
}
