package com.expenseanalyzer.processing.e2e;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole pipeline on real infrastructure: upload -> RustFS + outbox -> Kafka -> worker -> transactions.
 * Needs Docker.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=POSTGRESQL",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.listener.auto-startup=true",
        "app.outbox.scheduler.enabled=true",
        "app.outbox.poll-interval-ms=200"
})
class StatementPipelineE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    @Container
    static final GenericContainer<?> RUSTFS = new GenericContainer<>("rustfs/rustfs:latest")
            .withEnv("RUSTFS_ACCESS_KEY", "e2eaccess")
            .withEnv("RUSTFS_SECRET_KEY", "e2esecret123")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("app.storage.endpoint", () -> "http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000));
        r.add("app.storage.access-key", () -> "e2eaccess");
        r.add("app.storage.secret-key", () -> "e2esecret123");
        r.add("app.storage.bucket", () -> "statements-e2e");
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void uploadedStatementIsProcessedIntoTransactions() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User user = new User();
        user.setEmail("e2e@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setRole(UserRole.USER);
        user = userRepository.save(user);
        String bearer = "Bearer " + jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null));
        byte[] csv = """
                Date,Description,Debit,Credit
                02/01/2026,UPI-SWIGGY-ORDER,450.00,
                03/01/2026,SALARY JAN,,85000.00
                """.getBytes(StandardCharsets.UTF_8);

        String body = mvc.perform(multipart("/api/statements")
                        .file(new MockMultipartFile("file", "jan.csv", "text/csv", csv))
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (statementRepository.findById(id).orElseThrow().getStatus() != StatementStatus.COMPLETED
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }

        assertThat(statementRepository.findById(id).orElseThrow().getStatus()).isEqualTo(StatementStatus.COMPLETED);
        assertThat(transactionRepository.findByUserIdAndStatementId(user.getId(), id))
                .extracting(Transaction::getRawDescription)
                .containsExactlyInAnyOrder("UPI-SWIGGY-ORDER", "SALARY JAN");
        mvc.perform(get("/api/statements/" + id + "/status").header("Authorization", bearer))
                .andExpect(status().isOk());
    }
}
