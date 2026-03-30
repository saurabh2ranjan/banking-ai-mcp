package com.banking.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TracingConfig — bridge filter")
@ExtendWith(MockitoExtension.class)
class TracingConfigTest {

    @Mock
    private Tracer tracer;
    @Mock
    private Span span;
    @Mock
    private TraceContext traceContext;

    private OncePerRequestFilter filter;

    @BeforeEach
    void setUp() {
        var config = new TracingConfig();
        filter = config.tracingBridgeFilter(tracer);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("when OTel span is active")
    class WithActiveSpan {

        @BeforeEach
        void setUpSpan() {
            when(tracer.currentSpan()).thenReturn(span);
            when(span.context()).thenReturn(traceContext);
            when(traceContext.spanId()).thenReturn("abc123span");
        }

        @Test
        @DisplayName("tags the span with correlation.id from MDC")
        void tagsSpanWithCorrelationId() throws Exception {
            MDC.put("traceId", "corr-id-999");

            filter.doFilter(request(), response(), new MockFilterChain());

            verify(span).tag("correlation.id", "corr-id-999");
        }

        @Test
        @DisplayName("writes spanId and correlationId into MDC during filter chain")
        void writesMdcDuringChain() throws Exception {
            MDC.put("traceId", "corr-id-abc");

            String[] capturedSpanId = new String[1];
            String[] capturedCorrelationId = new String[1];
            filter.doFilter(request(), response(), (rq, rs) -> {
                capturedSpanId[0] = MDC.get("spanId");
                capturedCorrelationId[0] = MDC.get("correlationId");
            });

            assertThat(capturedSpanId[0]).isEqualTo("abc123span");
            assertThat(capturedCorrelationId[0]).isEqualTo("corr-id-abc");
        }

        @Test
        @DisplayName("clears spanId and correlationId from MDC after request")
        void clearsMdcAfterRequest() throws Exception {
            MDC.put("traceId", "corr-id-xyz");

            filter.doFilter(request(), response(), new MockFilterChain());

            assertThat(MDC.get("spanId")).isNull();
            assertThat(MDC.get("correlationId")).isNull();
        }

        @Test
        @DisplayName("clears MDC even when chain throws exception")
        void clearsMdcOnException() throws Exception {
            MDC.put("traceId", "corr-id-err");

            try {
                filter.doFilter(request(), response(),
                        (rq, rs) -> { throw new RuntimeException("downstream failure"); });
            } catch (RuntimeException ignored) {
                // expected
            }

            assertThat(MDC.get("spanId")).isNull();
            assertThat(MDC.get("correlationId")).isNull();
        }

        @Test
        @DisplayName("handles null correlationId in MDC gracefully")
        void handlesNullCorrelationId() throws Exception {
            // traceId not set in MDC — simulates edge case

            filter.doFilter(request(), response(), new MockFilterChain());

            verify(span).tag("correlation.id", "unknown");
        }
    }

    @Nested
    @DisplayName("when no OTel span is active")
    class WithNoSpan {

        @Test
        @DisplayName("passes through without modifying MDC")
        void passesThroughWithoutMdcChanges() throws Exception {
            when(tracer.currentSpan()).thenReturn(null);
            MDC.put("traceId", "corr-id-no-span");

            filter.doFilter(request(), response(), new MockFilterChain());

            // Only traceId should remain (set by CorrelationIdFilter, not this filter)
            assertThat(MDC.get("spanId")).isNull();
            assertThat(MDC.get("correlationId")).isNull();
        }
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/test");
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
