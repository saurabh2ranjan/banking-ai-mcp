package com.banking.gateway.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ─── Header forwarding ────────────────────────────────────────────────────

    @Nested
    @DisplayName("X-Correlation-ID header propagation")
    class HeaderPropagation {

        @Test
        @DisplayName("incoming header is echoed back in the response")
        void incomingHeader_isEchoedInResponse() throws Exception {
            var req  = request("my-trace-id-123");
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, new MockFilterChain());

            assertThat(resp.getHeader("X-Correlation-ID")).isEqualTo("my-trace-id-123");
        }

        @Test
        @DisplayName("when no header is present, a UUID is generated and returned")
        void noHeader_generatesUuidAndReturnsIt() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/test");
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, new MockFilterChain());

            String returned = resp.getHeader("X-Correlation-ID");
            assertThat(returned).isNotNull().isNotBlank();
            // must be a valid UUID
            assertThat(returned).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("blank header is treated as absent — a new UUID is generated")
        void blankHeader_generatesNewUuid() throws Exception {
            var req = new MockHttpServletRequest("GET", "/api/test");
            req.addHeader("X-Correlation-ID", "   ");
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, new MockFilterChain());

            assertThat(resp.getHeader("X-Correlation-ID"))
                .isNotNull()
                .doesNotContain("   ");
        }

        @Test
        @DisplayName("different requests get different UUIDs when no header is supplied")
        void twoRequests_getDistinctUuids() throws Exception {
            var resp1 = new MockHttpServletResponse();
            var resp2 = new MockHttpServletResponse();

            filter.doFilterInternal(new MockHttpServletRequest("GET", "/a"), resp1, new MockFilterChain());
            filter.doFilterInternal(new MockHttpServletRequest("GET", "/b"), resp2, new MockFilterChain());

            assertThat(resp1.getHeader("X-Correlation-ID"))
                .isNotEqualTo(resp2.getHeader("X-Correlation-ID"));
        }
    }

    // ─── MDC lifecycle ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MDC lifecycle")
    class MdcLifecycle {

        @Test
        @DisplayName("MDC traceId is set to the correlation ID while the filter chain executes")
        void mdcIsSetDuringFilterChain() throws Exception {
            var req  = request("trace-abc");
            var resp = new MockHttpServletResponse();

            // capture MDC value from inside the chain
            String[] mdcDuringChain = new String[1];
            filter.doFilterInternal(req, resp, (rq, rs) ->
                    mdcDuringChain[0] = MDC.get("traceId"));

            assertThat(mdcDuringChain[0]).isEqualTo("trace-abc");
        }

        @Test
        @DisplayName("MDC traceId is cleared after the filter chain completes")
        void mdcIsClearedAfterRequest() throws Exception {
            var req  = request("trace-xyz");
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, new MockFilterChain());

            assertThat(MDC.get("traceId")).isNull();
        }

        @Test
        @DisplayName("MDC traceId is cleared even if the chain throws an exception")
        void mdcIsClearedOnException() throws Exception {
            var req  = request("trace-err");
            var resp = new MockHttpServletResponse();

            try {
                filter.doFilterInternal(req, resp,
                    (rq, rs) -> { throw new RuntimeException("downstream failure"); });
            } catch (RuntimeException ignored) {
                // expected
            }

            assertThat(MDC.get("traceId")).isNull();
        }

        @Test
        @DisplayName("generated UUID is placed in MDC when no header is supplied")
        void generatedUuid_isPlacedInMdc() throws Exception {
            var req  = new MockHttpServletRequest("GET", "/api/test");
            var resp = new MockHttpServletResponse();

            String[] mdcDuringChain = new String[1];
            filter.doFilterInternal(req, resp, (rq, rs) ->
                    mdcDuringChain[0] = MDC.get("traceId"));

            // MDC value should match the response header (same generated UUID)
            assertThat(mdcDuringChain[0])
                .isNotNull()
                .isEqualTo(resp.getHeader("X-Correlation-ID"));
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest request(String correlationId) {
        var req = new MockHttpServletRequest("GET", "/api/test");
        req.addHeader("X-Correlation-ID", correlationId);
        return req;
    }
}
