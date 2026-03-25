package com.banking.gateway.consumer;

import com.banking.events.payment.PaymentStatusChangedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes payment status change events for analytics and metrics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class PaymentStatusConsumer {

    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "banking.payments.status", groupId = "payment-analytics-group")
    public void handlePaymentStatusChanged(PaymentStatusChangedEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("traceId", correlationId);
        try {
            log.info("Payment status event: {} | {} -> {} | {} {} | type={}",
                    event.paymentId(), event.previousStatus(), event.newStatus(),
                    event.amount(), event.currency(), event.paymentType());

            meterRegistry.counter("banking.payments.status.transitions",
                    "from", event.previousStatus() != null ? event.previousStatus() : "NONE",
                    "to", event.newStatus()
            ).increment();
        } finally {
            if (correlationId != null) MDC.remove("traceId");
        }
    }
}
