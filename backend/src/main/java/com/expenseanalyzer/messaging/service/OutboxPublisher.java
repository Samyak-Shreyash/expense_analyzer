package com.expenseanalyzer.messaging.service;

import com.expenseanalyzer.messaging.config.KafkaTopics;
import com.expenseanalyzer.messaging.model.OutboxEvent;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends unpublished outbox events to Kafka, keyed by aggregate id (per-statement ordering).
 * Delivery is at-least-once: a crash after send but before marking resends, so consumers de-duplicate.
 */
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    public static final int MAX_ATTEMPTS = 10;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public void publishPending() {
        for (OutboxEvent e : repository.findTop100ByPublishedAtIsNullAndAttemptCountLessThanOrderByCreatedAtAsc(MAX_ATTEMPTS)) {
            try {
                kafka.send(KafkaTopics.forEventType(e.getEventType()), e.getAggregateId().toString(), e.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                e.setPublishedAt(Instant.now());
                e.setLastError(null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException ex) {
                Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                e.setAttemptCount(e.getAttemptCount() + 1);
                e.setLastError(cause.getClass().getSimpleName());
                log.warn("Outbox publish failed: eventId={} type={} attempt={} error={}",
                        e.getId(), e.getEventType(), e.getAttemptCount(), e.getLastError());
            }
            repository.save(e);
        }
    }
}
