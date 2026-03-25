package com.banking.account.event;

import com.banking.events.notification.EmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards account Spring application events to Kafka after transaction commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class AccountEventPublisher {

    private static final String TOPIC_NOTIFICATIONS = "banking.notifications";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailNotification(EmailNotificationEvent event) {
        try {
            kafkaTemplate.send(TOPIC_NOTIFICATIONS, event.customerId(), event);
            log.debug("Published EmailNotificationEvent for customer {}", event.customerId());
        } catch (Exception e) {
            log.error("Failed to publish EmailNotificationEvent: {}", e.getMessage());
        }
    }
}
