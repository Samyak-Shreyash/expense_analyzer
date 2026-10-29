package com.expenseanalyzer.messaging.config;

import com.expenseanalyzer.messaging.service.OutboxService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topic names and declarations. The broker does not auto-create topics. */
@Configuration
public class KafkaTopics {

    public static final String STATEMENT_UPLOADED = "statement.uploaded";

    public static String forEventType(String eventType) {
        return switch (eventType) {
            case OutboxService.STATEMENT_UPLOADED -> STATEMENT_UPLOADED;
            default -> throw new IllegalArgumentException("No topic for event type " + eventType);
        };
    }

    @Bean
    public NewTopic statementUploadedTopic() {
        return TopicBuilder.name(STATEMENT_UPLOADED).partitions(3).replicas(1).build();
    }
}
