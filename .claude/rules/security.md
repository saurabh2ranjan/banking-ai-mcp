# Security Rules

## PII & Sensitive Data
- **Never log PII** — customer name, email, phone, SSN, date of birth, address are forbidden in any log statement
- Safe to log: `customerId`, `accountId`, `paymentId`, `correlationId`, status codes
- Mask account numbers in logs: log last 4 digits only — `****1234`
- Never include PII in exception messages that propagate to HTTP responses
- Never store PII in session objects, HTTP headers, or URL query parameters

```java
// Correct
log.info("[{}] Payment initiated for account ****{}", correlationId, accountNumber.substring(accountNumber.length()-4));

// Wrong
log.info("Payment for customer {} ({}) account {}", customer.getName(), customer.getEmail(), accountNumber);
```

## Secret Management
- No API keys, passwords, DB credentials, or tokens in source code or test fixtures
- All secrets come from environment variables — reference `${ENV_VAR_NAME}` in `application.yml` only
- `OPENAI_API_KEY` and `BANKING_API_KEY` must never appear as literal strings anywhere in code
- `.env` files must be in `.gitignore` — never commit them

## Input Validation
- Validate all input at system boundaries: REST controllers and MCP tool methods
- Use `@Validated` + `@NotNull`, `@NotBlank`, `@Positive` on request DTOs
- Never trust AI-provided tool arguments without validation — an LLM can hallucinate parameter values
- Reject requests with amounts ≤ 0 before they reach the service layer
- AccountId and CustomerId must be validated as valid UUID format at the boundary

## Exception & Error Handling
- Never expose stack traces in HTTP responses — the `GlobalExceptionHandler` must sanitize all errors
- Never use `e.getMessage()` directly in API error responses — use a safe, pre-defined message from the exception type
- Typed exceptions from `banking-common` map to specific HTTP status codes — extend the hierarchy, do not add `@ResponseStatus` on arbitrary classes
- Catch the most specific exception type — never `catch (Exception e)` as a catch-all in service code

## Audit & Traceability
- All state-changing operations (account creation, payments, KYC decisions, fraud holds) are covered by `AuditLogAspect` in the gateway — do not duplicate audit logging in service layers
- Every log line in a request context must include `correlationId` via MDC — use `[{}]` pattern with `traceId` from MDC
- Never remove or bypass the `CorrelationIdFilter` — it is `@Order(HIGHEST_PRECEDENCE)`
- When the `tracing` profile is active, `TracingBridgeFilter` (`@Order(HIGHEST_PRECEDENCE + 1)`) bridges the correlation ID to OTel spans and enriches MDC with `spanId` and `correlationId` for structured JSON logging
- OTel trace IDs and correlation IDs are safe to log — they are infrastructure identifiers, not PII

## Financial Integrity
- Monetary amounts must use the `Money` type — `double`/`float` are forbidden for financial values (precision loss)
- Payment operations must be idempotent — always check for existing payment before creating
- Fund holds must be placed before any external call — never after
- `@Version` on all entities is mandatory for optimistic locking — prevents double-spend on concurrent updates

## API Security
- The `BANKING_API_KEY` header check is enforced by Spring Security — never bypass it with `permitAll()` for business endpoints
- MCP endpoints (`/sse`, `/mcp/message`) are intentionally public — do not add auth to them
- `/actuator/prometheus` is public for Prometheus metrics scraping — do not add auth to it
- H2 console (`/h2-console`) must only be enabled in `dev` profile — never in production config
- Never add `@CrossOrigin("*")` — CORS is configured centrally in the security config
