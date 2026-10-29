package com.expenseanalyzer.messaging.service;

import com.expenseanalyzer.messaging.model.OutboxEvent;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class OutboxPublisherTest {

    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private OutboxEventRepository repository;

    @MockitoBean
    private KafkaTemplate<String, String> kafka;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
        MDC.clear();
    }

    private OutboxEvent enqueue(UUID userId, UUID statementId) {
        return outboxService.enqueue(OutboxService.STATEMENT_UPLOADED, statementId, userId,
                Map.of("statementId", statementId, "storageKey", "users/u/statements/s"));
    }

    @Test
    void enqueuedEventCarriesVersionedEnvelopeAndCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        UUID user = UUID.randomUUID();
        UUID stmt = UUID.randomUUID();

        OutboxEvent e = enqueue(user, stmt);

        String json = e.getPayload();
        assertThat((String) JsonPath.read(json, "$.eventId")).isEqualTo(e.getId().toString());
        assertThat((String) JsonPath.read(json, "$.eventType")).isEqualTo("StatementUploaded");
        assertThat((Integer) JsonPath.read(json, "$.eventVersion")).isEqualTo(1);
        assertThat((String) JsonPath.read(json, "$.correlationId")).isEqualTo("corr-123");
        assertThat((String) JsonPath.read(json, "$.userId")).isEqualTo(user.toString());
        assertThat((String) JsonPath.read(json, "$.aggregateId")).isEqualTo(stmt.toString());
        assertThat((String) JsonPath.read(json, "$.payload.storageKey")).isEqualTo("users/u/statements/s");
        assertThat((String) JsonPath.read(json, "$.occurredAt")).isNotBlank();
    }

    @Test
    void pendingEventIsSentKeyedByAggregateAndMarkedPublished() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        UUID stmt = UUID.randomUUID();
        OutboxEvent e = enqueue(UUID.randomUUID(), stmt);

        publisher.publishPending();

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(org.mockito.ArgumentMatchers.eq("statement.uploaded"),
                org.mockito.ArgumentMatchers.eq(stmt.toString()), value.capture());
        assertThat(value.getValue()).isEqualTo(e.getPayload());
        assertThat(repository.findById(e.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void publishedEventsAreNotResent() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        enqueue(UUID.randomUUID(), UUID.randomUUID());

        publisher.publishPending();
        publisher.publishPending();

        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedSendIsRecordedAndRetriedOnNextRun() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxEvent e = enqueue(UUID.randomUUID(), UUID.randomUUID());

        publisher.publishPending();

        OutboxEvent failed = repository.findById(e.getId()).orElseThrow();
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastError()).isEqualTo("KafkaException");

        publisher.publishPending();

        assertThat(repository.findById(e.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void eventsThatExhaustedRetriesAreSkipped() {
        OutboxEvent e = enqueue(UUID.randomUUID(), UUID.randomUUID());
        e.setAttemptCount(OutboxPublisher.MAX_ATTEMPTS);
        repository.save(e);

        publisher.publishPending();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
