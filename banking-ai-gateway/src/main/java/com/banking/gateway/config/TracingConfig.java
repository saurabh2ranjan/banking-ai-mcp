package com.banking.gateway.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bridges the custom {@code CorrelationIdFilter}'s MDC with OpenTelemetry spans.
 *
 * <p>Runs at {@code HIGHEST_PRECEDENCE + 1} — immediately after {@code CorrelationIdFilter}
 * which sets {@code MDC["traceId"]} to the business correlation ID.</p>
 *
 * <p>This filter:
 * <ol>
 *   <li>Reads the correlation ID from {@code MDC["traceId"]} (set by CorrelationIdFilter)</li>
 *   <li>Tags the current OTel span with {@code correlation.id} for Tempo search</li>
 *   <li>Writes OTel's own {@code traceId} and {@code spanId} into MDC for structured logging</li>
 *   <li>Copies the correlation ID to {@code MDC["correlationId"]} for the JSON log encoder</li>
 * </ol>
 *
 * <p>Only active when the {@code tracing} Spring profile is enabled.</p>
 */
@Slf4j
@Configuration
@Profile("tracing")
public class TracingConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public OncePerRequestFilter tracingBridgeFilter(Tracer tracer) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String correlationId = MDC.get("traceId");

                var currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    // Tag OTel span with business correlation ID for Tempo search
                    currentSpan.tag("correlation.id", correlationId != null ? correlationId : "unknown");

                    // Write OTel IDs into MDC for structured JSON logging
                    var context = currentSpan.context();
                    MDC.put("spanId", context.spanId());
                    MDC.put("correlationId", correlationId != null ? correlationId : "");
                }

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("spanId");
                    MDC.remove("correlationId");
                }
            }
        };
    }
}
