package com.expenseanalyzer.messaging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** Retry policy for listener failures (transient: storage or DB unavailable). */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Retries with backoff (1 s doubling, up to 5 min in total), then logs and moves on.
     * The statement stays QUEUED and can be re-queued; nothing is half-imported because
     * each attempt is one DB transaction.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(60_000L);
        backOff.setMaxElapsedTime(300_000L);
        return new DefaultErrorHandler(backOff);
    }
}
