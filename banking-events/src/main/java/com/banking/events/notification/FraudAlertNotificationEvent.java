package com.banking.events.notification;

import com.banking.events.EventMetadata;

public record FraudAlertNotificationEvent(
        String customerId,
        String paymentId,
        double fraudScore,
        String riskLevel,
        EventMetadata metadata
) implements NotificationEvent {
}
