package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.messaging.config.KafkaTopics;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka entry point for statement processing. The offset is committed only after
 * {@link StatementProcessor#process} returns, so a failure leads to redelivery.
 */
@Component
@RequiredArgsConstructor
public class StatementUploadedListener {

    private final StatementProcessor processor;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopics.STATEMENT_UPLOADED, groupId = StatementProcessor.CONSUMER,
            concurrency = "${app.worker.concurrency:2}")
    public void onMessage(String message) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, correlationId(message))) {
            processor.process(message);
        }
    }

    private String correlationId(String message) {
        try {
            return jsonMapper.readTree(message).path("correlationId").asString(null);
        } catch (RuntimeException e) {
            return null; // the processor reports malformed messages
        }
    }
}
