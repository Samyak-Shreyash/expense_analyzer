package com.expenseanalyzer.audit;

import com.expenseanalyzer.audit.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditLogPurgeTest {

    @Autowired
    private AuditService auditService;
    @Autowired
    private JdbcTemplate jdbc;

    private final Instant now = Instant.now();
    private final UUID user = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_logs");
    }

    private UUID insert(UUID userId, Duration age) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, user_id, action, occurred_at) VALUES (?, ?, 'AUTH_LOGIN_SUCCEEDED', ?)",
                id, userId, Timestamp.from(now.minus(age)));
        return id;
    }

    private List<UUID> remaining() {
        return jdbc.queryForList("SELECT id FROM audit_logs", UUID.class);
    }

    @Test
    void rowsWithUserAtAndPastNinetyDaysAreRemoved() {
        insert(user, Duration.ofDays(91));
        insert(user, Duration.ofDays(90).plusSeconds(1));
        UUID newer = insert(user, Duration.ofDays(89));

        auditService.purgeExpired();

        assertThat(remaining()).containsExactly(newer);
    }

    @Test
    void rowsWithoutUserAtAndPastThirtyDaysAreRemoved() {
        insert(null, Duration.ofDays(31));
        insert(null, Duration.ofDays(30).plusSeconds(1));
        UUID newer = insert(null, Duration.ofDays(29));

        auditService.purgeExpired();

        assertThat(remaining()).containsExactly(newer);
    }

    @Test
    void userRowsBetweenThirtyAndNinetyDaysStay() {
        UUID kept = insert(user, Duration.ofDays(60));

        auditService.purgeExpired();

        assertThat(remaining()).containsExactly(kept);
    }
}
