package com.expenseanalyzer.messaging.service;

import com.expenseanalyzer.messaging.model.OutboxEvent;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes events to the outbox inside the caller's transaction. */
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String STATEMENT_UPLOADED = "StatementUploaded";
    static final int EVENT_VERSION = 1;
    private static final String PRODUCER = "statement-module";

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    @Transactional
    public OutboxEvent enqueue(String eventType, UUID aggregateId, UUID userId, Map<String, ?> payload) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", now.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        envelope.put("userId", userId);
        envelope.put("aggregateId", aggregateId);
        envelope.put("payload", payload);

        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setEventType(eventType);
        e.setAggregateId(aggregateId);
        e.setUserId(userId);
        e.setPayload(jsonMapper.writeValueAsString(envelope));
        e.setCreatedAt(now);
        return repository.save(e);
    }
}
