# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Collaboration Style

- Before making any change, ask clarifying questions if the intent, scope, or approach is unclear.
- Present findings and trade-offs first; wait for confirmation before editing files.
- When multiple design options exist, offer them with reasoning and let the user decide.
- Keep changes minimal and focused — do not refactor or improve beyond what was asked.

## Build & Test Commands
**IMPORTANT: After every code change, validate the build succeeds.**e

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

# Frontend (React 18 + Vite + TypeScript — port 5173)
cd banking-ai-frontend && npm install
npm run dev      # dev server with HMR
npm run build    # production build
npm run lint     # ESLint on src/**/*.{ts,tsx}

# Start the gateway in dev mode (enables H2 console, colored logs, SQL debug)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev OPENAI_API_KEY=sk-... ../gradlew bootRun

# Production stack
export OPENAI_API_KEY=sk-your-key-here
docker-compose up
```

**Required environment variables:**
- `OPENAI_API_KEY` — required for AI features
- `BANKING_API_KEY` — API authentication key (defaults to `banking-demo-key-2024`)
- `BANKING_KAFKA_ENABLED` — set `true` to activate Kafka publishers/consumers (or use `kafka` Spring profile)
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` — Kafka bootstrap servers (default: `localhost:9092,localhost:9094,localhost:9096`)
- `OTEL_EXPORTER_OTLP_ENDPOINT` — OTel Collector endpoint (default: `http://localhost:4318`); only used when `tracing` profile active
- `TRACING_SAMPLING_PROBABILITY` — trace sampling rate 0.0–1.0 (default: `1.0`); set to `0.1` for prod

**Runtime endpoints (dev):**
- Gateway API: `http://localhost:8080`
- Frontend: `http://localhost:5173` — requires `npm run dev` in `banking-ai-frontend/`
- MCP Client API: `http://localhost:8081/api/mcp`
- H2 Console: `http://localhost:8080/h2-console` — requires `dev` profile; JDBC URL `jdbc:h2:mem:bankingdb`, username `sa`, password _(empty)_
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`
- MCP SSE (Inspector): `http://localhost:8080/sse`
- MCP Messages: `http://localhost:8080/mcp/message`
- Kafka UI: `http://localhost:8090` — requires `--profile tools` (opt-in)
- Grafana: `http://localhost:3000` — requires `--profile tracing` (opt-in); admin/admin
- Tempo: `http://localhost:3200` — requires `--profile tracing` (opt-in)
- Loki: `http://localhost:3100` — requires `--profile tracing` (opt-in)
- Prometheus: `http://localhost:9090` — requires `--profile tracing` (opt-in)

**REST API examples:** `requests.http` in the repo root — open with IntelliJ HTTP Client or VS Code REST Client.

**Correlation ID tracing:**
Pass `X-Correlation-ID: <your-id>` on any request; the same value appears in every log line for that request, in every Kafka message header, and in all consumer logs downstream. If the header is absent a UUID is auto-generated. The value is always echoed back in the response header.

**Running with Kafka (brokers must be started first):**
```bash
# Step 1 — start only the Kafka brokers (no PostgreSQL/Redis needed for dev)
docker-compose up kafka-1 kafka-2 kafka-3

# Step 1 (with Kafka UI) — brokers + Kafka UI
docker-compose --profile tools up kafka-1 kafka-2 kafka-3
# Kafka UI → http://localhost:8090

# Step 2 — once all 3 brokers are healthy, start the gateway
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev,kafka OPENAI_API_KEY=sk-... ../gradlew bootRun

# Stop brokers when done
docker-compose stop kafka-1 kafka-2 kafka-3
```

> **Note:** `kafka-ui` is opt-in via `--profile tools` — it does not start with
> `docker-compose up` by default. Running `SPRING_PROFILES_ACTIVE=dev,kafka` without brokers
> running will produce `WARN Connection to node ...` logs — the app still starts but Kafka
> publishing/consuming will not work until brokers are available.

**Running with distributed tracing (OpenTelemetry + Grafana):**
```bash
# Step 1 — start tracing infrastructure
docker compose --profile tracing up

# Step 2 — start the gateway with tracing enabled
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev,tracing OPENAI_API_KEY=sk-... ../gradlew bootRun

# With Kafka + tracing
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev,kafka,tracing OPENAI_API_KEY=sk-... ../gradlew bootRun

# Open Grafana → http://localhost:3000 (admin/admin)
#   Explore → Tempo → search traces by service or correlation.id
#   Explore → Loki → search logs, click TraceID to jump to trace
```

> **Tracing infrastructure:** OTel Collector, Grafana Tempo, Loki, Promtail, Prometheus, and Grafana
> are opt-in via `--profile tracing`. The `tracing` Spring profile activates OTLP export and JSON
> structured logging. Sampling is 100% in dev; set `TRACING_SAMPLING_PROBABILITY=0.1` for prod.

**Running MCP client alongside gateway:**
```bash
# Terminal 1 — MCP server (gateway), with Kafka enabled (brokers must be up — see above)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev,kafka OPENAI_API_KEY=sk-... ../gradlew bootRun

# Terminal 2 — MCP client (with Kafka KYC consumer)
cd banking-mcp-client && SPRING_PROFILES_ACTIVE=kafka ../gradlew bootRun

# Without Kafka (dev only, no docker-compose needed)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev OPENAI_API_KEY=sk-... ../gradlew bootRun
```

> **Dev seeded data:** The `dev` profile auto-seeds three customers — Alice Johnson, Bob Smith, Charlie Brown — with accounts and transactions ready for immediate testing.

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
- **Kafka Feature Flag:** All custom Kafka beans gated by `@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")`. This guards your code but NOT Spring Boot's `KafkaAutoConfiguration` — that fires purely on classpath presence. When adding Kafka to any module, also exclude `KafkaAutoConfiguration` in `application-test.yml` to prevent connection attempts to `localhost:9092` in every test run.
- **Transactional Event Publishing:** `@TransactionalEventListener(phase = AFTER_COMMIT)` — Kafka messages sent only after DB commit.
- **Correlation ID Tracing:** `CorrelationIdFilter` (`@Order(HIGHEST_PRECEDENCE)`) reads/generates `X-Correlation-ID`, sets `MDC["traceId"]`.
- **OpenTelemetry Tracing:** `TracingConfig` (`@Profile("tracing")`) bridges correlation ID to OTel spans via `TracingBridgeFilter` at `@Order(HIGHEST_PRECEDENCE + 1)`. Auto-instrumented HTTP, Kafka, and JDBC spans export to OTel Collector via OTLP.
- **Structured Logging:** `logback-spring.xml` switches between colored console (dev) and JSON (`LogstashEncoder`) for Loki ingestion when `tracing` profile is active.
- **Spring Boot 4 Kafka:** Requires `org.springframework.boot:spring-boot-kafka` (separate from `spring-kafka`).

### Spring Profiles

| Profile(s) | Datasource | DDL strategy | H2 Console | Log format | Tracing |
|---|---|---|---|---|---|
| _(none)_ | H2 in-memory | `create-drop` | off | colored console | off |
| `dev` | H2 in-memory | `create-drop` | **on** | colored console + SQL DEBUG | off |
| `dev,tracing` | H2 in-memory | `create-drop` | **on** | JSON (Loki) | **on** (100% sampling) |
| `kafka` | H2 in-memory | `create-drop` | off | colored console | off |
| `kafka,tracing,prod` | PostgreSQL 16 | `validate` | off | JSON (Loki) | **on** (10% sampling) |
| `kafka,prod` | PostgreSQL 16 | `validate` | off | colored console | off |

- `dev` — adds colored console logging, H2 console, SQL + bind-param DEBUG
- `kafka` — activates Kafka producers/consumers (gated by `banking.kafka.enabled=true`)
- `tracing` — activates OpenTelemetry tracing, OTLP export, JSON structured logging; requires `docker compose --profile tracing up`
- `prod` — switches to PostgreSQL dialect, disables H2, tightens log levels; activated automatically by docker-compose

### Database

- **Dev/Test:** H2 in-memory (`dev` or no profile); driver `org.h2.Driver`; schema auto-created from JPA entities (`ddl-auto: create-drop`)
- **Production:** PostgreSQL 16 (`prod` profile via docker-compose); driver `org.postgresql.Driver`; schema must exist before startup (`ddl-auto: validate`); credentials from `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` env vars
- All entities use `@Version` for optimistic locking; all service write methods use `@Transactional`

### Code Coverage

JaCoCo enforces **70% minimum** line/branch coverage — `./gradlew build` fails below this threshold.
Excluded: `domain/`, `dto/`, `config/`, `*Application`, `exception/`, `events/`
