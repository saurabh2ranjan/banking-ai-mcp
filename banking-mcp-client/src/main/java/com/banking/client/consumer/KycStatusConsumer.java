package com.banking.client.consumer;

import com.banking.events.onboarding.KycStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Reacts to KYC status changes in real-time, replacing the hourly polling scheduler.
 * In production, this would push to a compliance dashboard or trigger automated workflows.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class KycStatusConsumer {

    @KafkaListener(topics = "banking.onboarding.kyc-status", groupId = "compliance-kyc-group")
    public void handleKycStatusChanged(KycStatusChangedEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.info("KYC status changed: customer={} | {} -> {} | name={} | reason={}",
                    event.customerId(), event.previousKycStatus(), event.newKycStatus(),
                    event.fullName(), event.rejectionReason());

            if ("REJECTED".equals(event.newKycStatus())) {
                log.warn("COMPLIANCE ACTION REQUIRED: KYC rejected for customer {} ({}). Reason: {}",
                        event.customerId(), event.fullName(), event.rejectionReason());
            }
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }
}
