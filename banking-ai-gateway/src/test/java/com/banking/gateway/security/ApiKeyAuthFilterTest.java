package com.banking.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyAuthFilter")
class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private MockFilterChain  chain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "validApiKey", "test-api-key-2024");
        chain  = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    // ─── Valid API key ─────────────────────────────────────────────────────────

    @Nested @DisplayName("Valid API key")
    class ValidKey {

        @Test
        void validKey_chainContinues_securityContextSet() throws Exception {
            var req  = buildRequest("/api/v1/customers", "test-api-key-2024", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(200);     // filter did not block
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        }

        @Test
        void validKey_withClientId_usesPrincipalFromClientId() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "test-api-key-2024", "my-service");
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getPrincipal()).isEqualTo("my-service");
        }

        @Test
        void validKey_withoutClientId_defaultsToApiClient() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "test-api-key-2024", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getPrincipal()).isEqualTo("api-client");
        }

        @Test
        void validKey_getsApiUserRole() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "test-api-key-2024", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_API_USER"));
        }
    }

    // ─── Invalid or missing API key ───────────────────────────────────────────

    @Nested @DisplayName("Invalid or missing API key")
    class InvalidKey {

        @Test
        void wrongKey_returns401() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "wrong-key", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void missingKey_returns401() throws Exception {
            var req  = buildRequest("/api/v1/accounts", null, null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(401);
        }

        @Test
        void emptyKey_returns401() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(401);
        }

        @Test
        void unauthorisedResponse_isJson() throws Exception {
            var req  = buildRequest("/api/v1/accounts", "bad-key", null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getContentType()).contains("application/json");
            assertThat(resp.getContentAsString()).contains("UNAUTHORIZED");
        }
    }

    // ─── Bypass paths ─────────────────────────────────────────────────────────

    @Nested @DisplayName("Bypass paths (actuator / h2-console)")
    class BypassPaths {

        @Test
        void actuatorPath_bypassesAuthCheck() throws Exception {
            var req  = buildRequest("/actuator/health", null, null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(200);  // chain continued without 401
        }

        @Test
        void h2ConsolePath_bypassesAuthCheck() throws Exception {
            var req  = buildRequest("/h2-console/", null, null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(200);
        }

        @Test
        void apiPath_doesNotBypass() throws Exception {
            var req  = buildRequest("/api/v1/test", null, null);
            var resp = new MockHttpServletResponse();

            filter.doFilterInternal(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(401);
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest buildRequest(String uri, String apiKey, String clientId) {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (apiKey  != null) req.addHeader("X-API-Key",   apiKey);
        if (clientId != null) req.addHeader("X-Client-ID", clientId);
        return req;
    }
}
