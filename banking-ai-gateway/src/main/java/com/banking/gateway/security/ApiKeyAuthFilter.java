package com.banking.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API key authentication filter.
 * In production, replace with JWT + OAuth2 (Spring Authorization Server).
 * Headers:
 *   X-API-Key: <key>
 *   X-Client-ID: <client-name>
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CLIENT_HEADER  = "X-Client-ID";

    @Value("${banking.security.api-key:banking-demo-key-2024}")
    private String validApiKey;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain) throws ServletException, IOException {

        // Skip auth for actuator and H2 console
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/h2-console") || uri.equals("/sse") || uri.startsWith("/mcp")) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey  = request.getHeader(API_KEY_HEADER);
        String clientId = request.getHeader(CLIENT_HEADER);

        if (validApiKey.equals(apiKey)) {
            String principal = clientId != null ? clientId : "api-client";
            var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid or missing API key\",\"errorCode\":\"UNAUTHORIZED\"}");
        }
    }
}
