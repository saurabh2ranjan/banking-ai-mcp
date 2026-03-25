package com.banking.notification.consumer;

import com.banking.events.notification.EmailNotificationEvent;
import com.banking.events.notification.FraudAlertNotificationEvent;
import com.banking.events.notification.SmsNotificationEvent;
import com.banking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes notification events from Kafka and delegates to NotificationService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "banking.notifications", groupId = "notification-delivery-group")
    public void handleEmailNotification(EmailNotificationEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.debug("Received EmailNotificationEvent: {} -> {}", event.customerId(), event.subject());
            notificationService.sendWelcomeEmail(event.email(), event.fullName());
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }

    @KafkaListener(topics = "banking.notifications", groupId = "notification-sms-group")
    public void handleSmsNotification(SmsNotificationEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.debug("Received SmsNotificationEvent: {}", event.customerId());
            notificationService.sendPaymentInitiatedSms(event.mobile(), "N/A",
                    java.math.BigDecimal.ZERO, "N/A");
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }

    @KafkaListener(topics = "banking.notifications", groupId = "notification-fraud-group")
    public void handleFraudAlertNotification(FraudAlertNotificationEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.debug("Received FraudAlertNotificationEvent: {}", event.paymentId());
            notificationService.sendFraudAlertToCompliance(event.paymentId(), event.fraudScore(), event.riskLevel());
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }
}
