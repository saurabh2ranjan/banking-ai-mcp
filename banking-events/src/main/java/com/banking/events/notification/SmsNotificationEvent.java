package com.banking.events.notification;

import com.banking.events.EventMetadata;

public record SmsNotificationEvent(
        String customerId,
        String mobile,
        String message,
        EventMetadata metadata
) implements NotificationEvent {
}
