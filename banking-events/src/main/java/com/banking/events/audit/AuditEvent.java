package com.banking.events.audit;

import com.banking.events.EventMetadata;

/**
 * Structured audit event published by the AuditLogAspect.
 * Partition key: caller.
 */
public record AuditEvent(
        String caller,
        String method,
        String outcome,
        long elapsedMs,
        String errorMessage,
        EventMetadata metadata
) {
}
