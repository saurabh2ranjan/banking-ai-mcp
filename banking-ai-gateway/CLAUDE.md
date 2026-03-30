# banking-ai-gateway — Module Rules

## Purpose
Spring Boot entry point. Owns: MCP server, ChatClient (GPT-4o), REST API, AOP audit logging, Kafka consumers, security config, correlation ID filter.

## This Module Is the Integration Hub — Rules
- This is the ONLY module that imports multiple domain modules simultaneously
- Do not add domain business logic here — delegate to the appropriate `*Service`
- Controllers are thin: validate input → call service/ChatClient → return `ApiResponse`
- All MCP tool beans from domain modules are registered here in `BankingAiConfig`

## Package Ownership
```
config/      ← BankingAiConfig (tool registration), SecurityConfig, WebConfig, TracingConfig
controller/  ← REST endpoints + GlobalExceptionHandler
filter/      ← CorrelationIdFilter (@Order(HIGHEST_PRECEDENCE))
audit/       ← AuditLogAspect (@Around all controllers)
consumer/    ← Kafka listeners (gated by banking.kafka.enabled)
kafka/       ← Kafka producer interceptor (stamps correlationId header)
security/    ← API key validation, public endpoint whitelist
service/     ← ChatSessionService (in-memory session store)
```

## BankingAiConfig Rules
- Register ALL `*McpTool` beans here — never register tools ad-hoc in controllers
- Always unwrap AOP proxies: `AopProxyUtils.getSingletonTarget(bean)` before passing to `MethodToolCallbackProvider`
- System prompt must define the assistant's role, capabilities, and constraints explicitly
- Use a single `ChatClient` bean — never create new instances per request

## CorrelationIdFilter Rules
- Must remain `@Order(Ordered.HIGHEST_PRECEDENCE)` — it must run before any other filter
- Always set `MDC.put("traceId", correlationId)` and clear in `finally`
- Always echo back the correlation ID in the response header `X-Correlation-ID`
- Never remove this filter or move its order

## TracingConfig Rules (`@Profile("tracing")`)
- `TracingBridgeFilter` runs at `@Order(HIGHEST_PRECEDENCE + 1)` — immediately after `CorrelationIdFilter`
- Bridges the custom correlation ID to OTel: tags the current span with `correlation.id` attribute
- Writes OTel `spanId` and `correlationId` into MDC for structured JSON logging (Loki)
- Must clean up MDC keys (`spanId`, `correlationId`) in `finally` — same pattern as `CorrelationIdFilter`
- Never move this filter before `CorrelationIdFilter` — it depends on `MDC["traceId"]` being set first
- Only active when `tracing` profile is enabled — zero overhead in dev/prod without tracing

## AuditLogAspect Rules
- Intercepts all `@RestController` methods — do not add audit logging in service layers
- Logs: caller identity, method name, duration, outcome (success/failure)
- Must NOT log request body contents (may contain PII or financial data)

## GlobalExceptionHandler Rules
- Catches all typed exceptions from `banking-common` hierarchy
- Returns `ApiResponse.error(errorCode, safeMessage)` — never raw exception details
- Must handle: `NotFoundException` (404), `ValidationException` (400), `BusinessRuleException` (422), `InsufficientFundsException` (422), `KycNotApprovedException` (403), `KycFailedException` (500)
- New exception types added to `banking-common` must have a corresponding handler added here

## Security Config Rules
- `BANKING_API_KEY` header is required for all business endpoints
- Public endpoints (no auth): `/sse`, `/mcp/message`, `/actuator/health`, `/actuator/prometheus`, `/h2-console/**` (dev only)
- `/actuator/prometheus` is public for Prometheus scraping (metrics collection)
- H2 console is controlled via Spring profiles: enabled in `application-dev.yml`, explicitly disabled in `application-prod.yml` and the base `application.yml` — do not use `@Profile("dev")` on a bean
- Never add `permitAll()` to a business endpoint without explicit approval

## Logging Rules
- Log patterns are managed by `logback-spring.xml` — do NOT add `logging.pattern.console` to YAML files
- `logback-spring.xml` selects appender by profile: colored console (dev/default) or JSON via `LogstashEncoder` (tracing)
- `logging.level` entries in YAML files still work alongside `logback-spring.xml`

## Testing in This Module
- Controller tests: use `@WebMvcTest` + mock service dependencies
- Filter tests: use `MockMvc` with `@SpringBootTest(webEnvironment=MOCK)`
- Do not write integration tests that call real OpenAI API — mock the `ChatClient`
- `application-test.yml` excludes `KafkaAutoConfiguration` — this prevents Spring Boot from wiring a `ProducerFactory`/`KafkaTemplate` and attempting connections to `localhost:9092` on every test run
- `application-test.yml` also excludes OTel tracing auto-configuration (`OpenTelemetryTracingAutoConfiguration`, `OtlpTracingAutoConfiguration`, `OpenTelemetrySdkAutoConfiguration`, `OtlpMetricsExportAutoConfiguration`) and sets `management.tracing.enabled=false` to prevent OTLP connection attempts in tests
- Kafka consumer tests: use `@EmbeddedKafka` with a dedicated profile (e.g. `@ActiveProfiles("test", "kafka-test")`) that does NOT exclude `KafkaAutoConfiguration`, so embedded Kafka beans can be wired up
