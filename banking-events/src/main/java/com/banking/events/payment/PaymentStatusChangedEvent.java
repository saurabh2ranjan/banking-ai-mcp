package com.banking.events.payment;

import com.banking.events.EventMetadata;

import java.math.BigDecimal;

/**
 * Published on every payment state transition.
 * Partition key: sourceAccountId (preserves per-account ordering).
 */
public record PaymentStatusChangedEvent(
        String paymentId,
        String customerId,
        String sourceAccountId,
        String destinationAccountId,
        String previousStatus,
        String newStatus,
        BigDecimal amount,
        String currency,
        String paymentType,
        EventMetadata metadata
) {
}
