package com.banking.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes a correlation ID for every inbound request.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Read {@code X-Correlation-ID} from the request header; generate a UUID if absent.</li>
 *   <li>Store it in SLF4J MDC under the key {@code traceId} (used by the log pattern).</li>
 *   <li>Echo the value back in the response header so callers can correlate logs.</li>
 *   <li>Remove the MDC entry on the way out to prevent thread-pool leakage.</li>
 * </ol>
 *
 * <p>The MDC key {@code traceId} is picked up automatically by:
 * <ul>
 *   <li>The console log pattern ({@code [%X{traceId}]}).</li>
 *   <li>{@code EventMetadata} via {@code MDC.get("traceId")} in service layer.</li>
 *   <li>{@code CorrelationIdProducerInterceptor} which stamps it as a Kafka header.</li>
 * </ul>
 *
 * <p>Because this is a plain servlet filter ({@code @Order(HIGHEST_PRECEDENCE)}), it runs
 * for ALL requests — including MCP ({@code /sse}, {@code /mcp/**}) and actuator endpoints
 * that bypass Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
