package com.expenseanalyzer.transaction.controller;

import com.expenseanalyzer.support.PostgresUsers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

/** The same scenarios on the real schema: reversals net out in Postgres aggregates. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=POSTGRESQL"
})
class ReversalPostgresTest extends ReversalTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected void persistUser(UUID id) {
        PostgresUsers.insert(jdbc, id);
    }
}
