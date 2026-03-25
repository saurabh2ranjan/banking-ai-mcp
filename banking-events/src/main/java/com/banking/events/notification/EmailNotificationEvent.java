package com.banking.events.notification;

import com.banking.events.EventMetadata;

public record EmailNotificationEvent(
        String customerId,
        String email,
        String fullName,
        String subject,
        String body,
        EventMetadata metadata
) implements NotificationEvent {
}
