package com.banking.gateway.consumer;

import com.banking.events.audit.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes audit events for persistence.
 * Phase 1: logs to a structured format.
 * Phase 2: persist to audit_events table or forward to Elasticsearch.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class AuditEventConsumer {

    @KafkaListener(topics = "banking.audit.trail", groupId = "audit-persistence-group")
    public void handleAuditEvent(AuditEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.info("AUDIT_EVENT | {} | caller={} | method={} | outcome={} | elapsed={}ms | error={}",
                    event.metadata().timestamp(), event.caller(), event.method(),
                    event.outcome(), event.elapsedMs(), event.errorMessage());
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }
}
