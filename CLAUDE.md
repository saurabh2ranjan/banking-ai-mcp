# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
./gradlew clean build

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :banking-account:test
./gradlew :banking-ai-gateway:test
./gradlew :banking-payment:test
./gradlew :banking-fraud:test
./gradlew :banking-onboarding:test
./gradlew :banking-mcp-client:test

# Run a single test class
./gradlew :banking-account:test --tests "com.banking.account.mcp.AccountMcpToolTest"
./gradlew :banking-mcp-client:test --tests "com.banking.client.service.BankingMcpClientServiceTest"
./gradlew :banking-ai-gateway:test --tests "com.banking.gateway.filter.CorrelationIdFilterTest"

# Run with coverage
./gradlew test jacocoRootReport
# Report: build/reports/jacoco/root/html/index.html

# Start the gateway (dev profile loads demo data)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev ../gradlew bootRun

# Production stack
export OPENAI_API_KEY=sk-your-key-here
docker-compose up
```

**Required environment variables:**
- `OPENAI_API_KEY` — required for AI features
- `BANKING_API_KEY` — API authentication key (defaults to `banking-demo-key-2024`)
- `BANKING_KAFKA_ENABLED` — set `true` to activate Kafka publishers/consumers (or use `kafka` Spring profile)
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` — Kafka bootstrap servers (default: `localhost:9092,localhost:9094,localhost:9096`)

**Runtime endpoints (dev):**
- Gateway API: `http://localhost:8080`
- MCP Client API: `http://localhost:8081/api/mcp`
- H2 Console: `http://localhost:8080/h2-console`
- Health: `http://localhost:8080/actuator/health`
- MCP SSE (Inspector): `http://localhost:8080/sse`
- MCP Messages: `http://localhost:8080/mcp/message`
- Kafka UI: `http://localhost:8090` (docker-compose only)

**Correlation ID tracing:**
Pass `X-Correlation-ID: <your-id>` on any request; the same value appears in every log line for that request, in every Kafka message header, and in all consumer logs downstream. If the header is absent a UUID is auto-generated. The value is always echoed back in the response header.

**Running MCP client alongside gateway:**
```bash
# Terminal 1 — MCP server (gateway), with Kafka enabled
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev,kafka OPENAI_API_KEY=sk-... ../gradlew bootRun

# Terminal 2 — MCP client (with Kafka KYC consumer)
cd banking-mcp-client && SPRING_PROFILES_ACTIVE=kafka ../gradlew bootRun

# Without Kafka (dev only)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev OPENAI_API_KEY=sk-... ../gradlew bootRun
```

---

## Architecture

**Stack:** Spring Boot 4.0.3 · Java 25 · Spring AI 2.0.0-M2 · Gradle monorepo

### Module Dependency Graph

```
banking-common  (BaseEntity, Money, ApiResponse, exception hierarchy)
    ↑
    ├── banking-events        (Kafka event DTOs — pure records, no Spring)
    ├── banking-notification  (email/SMS/push events + Kafka consumer)
    ├── banking-onboarding    (customer lifecycle, KYC, AML — 5 MCP tools)
    ├── banking-account       (accounts, balance, limits, holds — 6 MCP tools)
    ├── banking-payment       (NEFT/RTGS/IMPS/UPI/SWIFT — 7 MCP tools)
    └── banking-fraud         (rule-based fraud detection — 2 MCP tools)
         ↑
banking-ai-gateway  (Spring Boot entry point — MCP server, ChatClient, REST API, Kafka consumers — port 8080)
         ↑  [MCP protocol]
banking-mcp-client  (standalone MCP client — direct tool invocation, Kafka KYC consumer — port 8081)
```

### MCP Server

The gateway runs an embedded **Spring AI MCP server** (`spring-ai-starter-mcp-server-webmvc`), configured in `application.yml`:

```yaml
spring.ai.mcp.server:
  enabled: true    # activates McpServerSseWebMvcAutoConfiguration
  name: banking-mcp-server
  version: 1.0.0
  type: SYNC       # API style: SYNC (blocking) vs ASYNC (reactive)
  protocol: SSE    # transport: registers /sse and /mcp/message endpoints
  capabilities:
    tool: true
```

MCP endpoints (no API key required):
- `GET  http://localhost:8080/sse`          — SSE connection (Inspector/client connects here)
- `POST http://localhost:8080/mcp/message`  — JSON-RPC tool calls

**Tool registration** (`BankingAiConfig`): `MethodToolCallbackProvider` reflects over all `*McpTool` beans at startup.
AOP proxy unwrapping (`AopProxyUtils.getSingletonTarget`) is **required** — without it, `@Tool` annotations are invisible to reflection and tools are silently dropped.

### MCP Client (`banking-mcp-client`)

A standalone Spring Boot app (`spring-ai-starter-mcp-client`, port 8081) that connects to the gateway's MCP server and invokes tools **directly without AI orchestration**.

`BankingMcpClientService` injects the auto-configured `McpSyncClient` bean; `ComplianceScheduler` is enabled via `banking.compliance.kyc-check-enabled=true` (disabled by default).

### How AI Orchestration Works

`banking-ai-gateway` runs a Spring AI `ChatClient` backed by GPT-4o. All domain modules expose `@Tool`-annotated methods (via `MethodToolCallbackProvider`). The gateway manages conversation sessions in-memory (max 1000 sessions, 50-message trim).

### Key Patterns

- **Fund Holds:** `initiatePayment` places an immediate hold on funds; released on complete or fail — prevents double-spending.
- **Fraud Engine:** Rule classes are `@Component` beans scored and summed. Add a new rule = add a new component implementing the rule interface.
- **AOP Audit Logging:** `AuditLogAspect` intercepts all controller calls — do not duplicate audit logging in services.
- **Exception Hierarchy:** Typed exceptions in `banking-common` map to HTTP status codes — do not add generic catch-all handlers.
- **DTO Mapping:** MapStruct `@Mapper` interfaces only — no manual entity-to-DTO conversion.
- **Kafka Feature Flag:** All Kafka code gated by `@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")`.
- **Transactional Event Publishing:** `@TransactionalEventListener(phase = AFTER_COMMIT)` — Kafka messages sent only after DB commit.
- **Correlation ID Tracing:** `CorrelationIdFilter` (`@Order(HIGHEST_PRECEDENCE)`) reads/generates `X-Correlation-ID`, sets `MDC["traceId"]`.
- **Spring Boot 4 Kafka:** Requires `org.springframework.boot:spring-boot-kafka` (separate from `spring-kafka`).

### Database

- **Dev/Test:** H2 in-memory; schema auto-created from JPA entities
- **Production:** PostgreSQL 16 (via docker-compose)
- All entities use `@Version` for optimistic locking; all service write methods use `@Transactional`

### Code Coverage

JaCoCo enforces **70% minimum** line/branch coverage — `./gradlew build` fails below this threshold.
Excluded: `domain/`, `dto/`, `config/`, `*Application`, `exception/`, `events/`
