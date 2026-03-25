package com.banking.onboarding.event;

import com.banking.events.notification.EmailNotificationEvent;
import com.banking.events.onboarding.KycStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards onboarding Spring application events to Kafka after transaction commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class OnboardingEventPublisher {

    private static final String TOPIC_KYC_STATUS = "banking.onboarding.kyc-status";
    private static final String TOPIC_NOTIFICATIONS = "banking.notifications";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKycStatusChanged(KycStatusChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_KYC_STATUS, event.customerId(), event);
            log.debug("Published KycStatusChangedEvent: {} -> {}", event.customerId(), event.newKycStatus());
        } catch (Exception e) {
            log.error("Failed to publish KycStatusChangedEvent for customer {}: {}",
                    event.customerId(), e.getMessage());
        }
    }

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
