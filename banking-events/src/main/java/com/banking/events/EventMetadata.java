package com.banking.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Common metadata embedded in every banking event.
 *
 * @param eventId       unique identifier for idempotency
 * @param timestamp     when the event was created
 * @param source        originating module (e.g. "banking-payment")
 * @param correlationId optional trace/correlation ID for distributed tracing
 */
public record EventMetadata(
        String eventId,
        Instant timestamp,
        String source,
        String correlationId
) {
    public static EventMetadata now(String source) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now(),
                source,
                null
        );
    }

    public static EventMetadata now(String source, String correlationId) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now(),
                source,
                correlationId
        );
    }
}
