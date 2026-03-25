package com.banking.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka infrastructure: topic creation and error handling.
 * Only active when banking.kafka.enabled=true.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    public static final String TOPIC_NOTIFICATIONS = "banking.notifications";
    public static final String TOPIC_PAYMENT_STATUS = "banking.payments.status";
    public static final String TOPIC_AUDIT_TRAIL = "banking.audit.trail";
    public static final String TOPIC_KYC_STATUS = "banking.onboarding.kyc-status";

    // ─── Topics ─────────────────────────────────────────────────────────────

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATIONS)
                .partitions(6)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic paymentStatusTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_STATUS)
                .partitions(12)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic auditTrailTopic() {
        return TopicBuilder.name(TOPIC_AUDIT_TRAIL)
                .partitions(6)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic kycStatusTopic() {
        return TopicBuilder.name(TOPIC_KYC_STATUS)
                .partitions(3)
                .replicas(3)
                .build();
    }

    // ─── Error Handling ─────────────────────────────────────────────────────

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        var backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxElapsedTime(10_000L);

        var handler = new DefaultErrorHandler(recoverer, backoff);
        log.info("Kafka error handler configured with exponential backoff and DLT recovery");
        return handler;
    }
}
