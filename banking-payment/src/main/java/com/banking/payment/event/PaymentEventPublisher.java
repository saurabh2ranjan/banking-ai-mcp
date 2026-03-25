package com.banking.payment.event;

import com.banking.events.EventMetadata;
import com.banking.events.notification.FraudAlertNotificationEvent;
import com.banking.events.notification.EmailNotificationEvent;
import com.banking.events.notification.SmsNotificationEvent;
import com.banking.events.payment.PaymentStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for Spring application events (published by PaymentService after commit)
 * and forwards them to Kafka topics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class PaymentEventPublisher {

    private static final String TOPIC_PAYMENT_STATUS = "banking.payments.status";
    private static final String TOPIC_NOTIFICATIONS = "banking.notifications";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_PAYMENT_STATUS, event.sourceAccountId(), event);
            log.debug("Published PaymentStatusChangedEvent: {} -> {}", event.paymentId(), event.newStatus());
        } catch (Exception e) {
            log.error("Failed to publish PaymentStatusChangedEvent for payment {}: {}",
                    event.paymentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSmsNotification(SmsNotificationEvent event) {
        try {
            kafkaTemplate.send(TOPIC_NOTIFICATIONS, event.customerId(), event);
            log.debug("Published SmsNotificationEvent for customer {}", event.customerId());
        } catch (Exception e) {
            log.error("Failed to publish SmsNotificationEvent: {}", e.getMessage());
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFraudAlertNotification(FraudAlertNotificationEvent event) {
        try {
            kafkaTemplate.send(TOPIC_NOTIFICATIONS, event.customerId(), event);
            log.debug("Published FraudAlertNotificationEvent for payment {}", event.paymentId());
        } catch (Exception e) {
            log.error("Failed to publish FraudAlertNotificationEvent: {}", e.getMessage());
        }
    }
}
