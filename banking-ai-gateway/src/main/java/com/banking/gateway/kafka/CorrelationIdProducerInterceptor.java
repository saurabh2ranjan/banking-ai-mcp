package com.banking.gateway.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ProducerInterceptor} that propagates the current request's correlation ID
 * across the Kafka boundary as a message header ({@code X-Correlation-ID}).
 *
 * <p><b>Why this works:</b> {@code @TransactionalEventListener(AFTER_COMMIT)} fires on the
 * same thread as the originating service method. SLF4J MDC is thread-local, so
 * {@code MDC.get("traceId")} still returns the value set by {@link
 * com.banking.gateway.filter.CorrelationIdFilter} when {@code kafkaTemplate.send()} is called.
 * Kafka's {@code ProducerInterceptor.onSend()} is invoked synchronously on the calling thread
 * before the record is handed off to the network I/O thread, so the MDC value is captured
 * at send time, not at acknowledgement time.
 *
 * <p><b>Registration:</b> This class is NOT a Spring bean — Kafka instantiates it directly
 * via the no-arg constructor. It is registered via:
 * <pre>
 * spring.kafka.producer.properties.interceptor.classes=\
 *     com.banking.gateway.kafka.CorrelationIdProducerInterceptor
 * </pre>
 *
 * <p>Consumers extract the header via {@code @Header("X-Correlation-ID")} and restore MDC.
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<String, Object> {

    private static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "traceId";

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null) {
            record.headers().add(HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op — no Spring DI available here
    }
}
