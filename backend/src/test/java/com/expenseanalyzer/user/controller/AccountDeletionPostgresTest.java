package com.expenseanalyzer.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The same scenarios on the real schema: H2 has no foreign keys, so only this proves the deletion order. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=POSTGRESQL"
})
class AccountDeletionPostgresTest extends AccountDeletionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** An import still writing when deletion starts: deletion waits for it, then removes what it wrote. */
    @Test
    void waitsForARunningWriteAndRemovesItsRows() throws Exception {
        TransactionStatus running = transactionManager.getTransaction(new DefaultTransactionDefinition());
        jdbc.update("INSERT INTO categories (id, user_id, name) VALUES (?, ?, 'late')", UUID.randomUUID(), alice);

        CompletableFuture<Integer> deletion = CompletableFuture.supplyAsync(() -> {
            try {
                return deleteAccount(ALICE, PASSWORD).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        waitForBlockedQuery();
        transactionManager.commit(running);

        assertThat(deletion.get(30, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(rows("categories", alice)).isZero();
        assertSoftDeleted(alice);
    }

    private void waitForBlockedQuery() throws InterruptedException {
        try (var monitor = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var query = monitor.createStatement()) {
            for (int i = 0; i < 300; i++) {
                try (var result = query.executeQuery(
                        "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")) {
                    result.next();
                    if (result.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(100);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        throw new AssertionError("deletion never blocked on the running write");
    }
}
