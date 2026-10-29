package com.expenseanalyzer.statement.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real RustFS container (S3 API), so it needs Docker. */
@Testcontainers
class S3StatementStorageTest {

    @Container
    static final GenericContainer<?> RUSTFS = new GenericContainer<>("rustfs/rustfs:latest")
            .withEnv("RUSTFS_ACCESS_KEY", "testaccess")
            .withEnv("RUSTFS_SECRET_KEY", "testsecret123")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    private static S3StatementStorage storage;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000);
        storage = new S3StatementStorage(endpoint, "us-east-1", "testaccess", "testsecret123", "statements-test");
    }

    @Test
    void storedObjectCanBeReadBack() throws Exception {
        String key = StatementStorage.keyFor(UUID.randomUUID(), UUID.randomUUID());
        byte[] body = "Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8);

        storage.store(key, new ByteArrayInputStream(body), body.length, "text/csv");

        try (InputStream in = storage.read(key)) {
            assertThat(in.readAllBytes()).isEqualTo(body);
        }
    }

    @Test
    void deletedObjectCannotBeRead() {
        String key = StatementStorage.keyFor(UUID.randomUUID(), UUID.randomUUID());
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        storage.store(key, new ByteArrayInputStream(body), body.length, "text/csv");

        storage.delete(key);

        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(StatementObjectNotFoundException.class);
    }

    @Test
    void deletingMissingObjectIsNoOp() {
        storage.delete(StatementStorage.keyFor(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void keyIsScopedByUserAndStatement() {
        UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID stmt = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(StatementStorage.keyFor(user, stmt))
                .isEqualTo("users/00000000-0000-0000-0000-000000000001/statements/00000000-0000-0000-0000-000000000002");
    }
}
