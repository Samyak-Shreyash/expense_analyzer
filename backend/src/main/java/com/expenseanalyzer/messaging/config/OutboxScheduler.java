package com.expenseanalyzer.messaging.config;

import com.expenseanalyzer.messaging.service.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls the outbox. Disabled in tests, which call the publisher directly. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publish() {
        publisher.publishPending();
    }
}
