package com.banking.events.notification;

import com.banking.events.EventMetadata;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all notification events.
 * Consumers can pattern-match on the concrete type to route delivery.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface NotificationEvent
        permits EmailNotificationEvent, SmsNotificationEvent, FraudAlertNotificationEvent {

    String customerId();
    EventMetadata metadata();
}
