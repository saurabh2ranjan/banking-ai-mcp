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
config/      ← BankingAiConfig (tool registration), SecurityConfig, WebConfig
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
- Public endpoints (no auth): `/sse`, `/mcp/message`, `/actuator/health`, `/h2-console/**` (dev only)
- H2 console must be disabled in production profile — guard with `@Profile("dev")`
- Never add `permitAll()` to a business endpoint without explicit approval

## Testing in This Module
- Controller tests: use `@WebMvcTest` + mock service dependencies
- Filter tests: use `MockMvc` with `@SpringBootTest(webEnvironment=MOCK)`
- Do not write integration tests that call real OpenAI API — mock the `ChatClient`
- Kafka consumer tests: use `@EmbeddedKafka`
