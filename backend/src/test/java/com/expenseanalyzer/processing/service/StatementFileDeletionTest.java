package com.expenseanalyzer.processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The uploaded file is deleted once imported unless the user turned that off; the rows stay either way. */
@SpringBootTest
class StatementFileDeletionTest {

    private static final String CSV = """
            Date,Description,Amount
            2026-01-03,Salary,50000.00
            """;

    @Autowired
    private StatementProcessor processor;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private S3StatementStorage storage;
    @MockitoBean
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private Statement imported(boolean strip) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setStripStatementPii(strip);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        Statement s = new Statement();
        s.setUserId(u.getId());
        s.setActive(true);
        s.setFormat(StatementFormat.CSV);
        s.setStatus(StatementStatus.QUEUED);
        s.setFileName("statement.csv");
        s.onCreate();
        s = statementRepository.saveAndFlush(s);
        s.setStorageKey(S3StatementStorage.keyFor(u.getId(), s.getId()));
        s = statementRepository.saveAndFlush(s);
        when(storage.read(s.getStorageKey()))
                .thenAnswer(inv -> new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));
        processor.process("""
                {"eventId":"%s","eventType":"StatementUploaded","eventVersion":1,"occurredAt":"2026-01-01T00:00:00Z",
                 "producer":"statement-module","correlationId":"corr-1","userId":"%s","aggregateId":"%s",
                 "payload":{"statementId":"%s","storageKey":"%s","format":"CSV","fileHash":"x"}}
                """.formatted(UUID.randomUUID(), u.getId(), s.getId(), s.getId(), s.getStorageKey()));
        return s;
    }

    @Test
    void theFileIsDeletedAfterImportByDefault() {
        Statement s = imported(true);

        Statement after = statementRepository.findById(s.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(StatementStatus.COMPLETED);
        assertThat(after.getStorageKey()).isNull();
        assertThat(transactionRepository.count()).isEqualTo(1);
        verify(storage).delete(s.getStorageKey());
    }

    @Test
    void theFileIsKeptWhenTheUserTurnedDeletionOff() {
        Statement s = imported(false);

        assertThat(statementRepository.findById(s.getId()).orElseThrow().getStorageKey()).isEqualTo(s.getStorageKey());
        verify(storage, never()).delete(s.getStorageKey());
    }
}
