# Banking AI MCP — Project Specification

## 1. Purpose & Goals

Banking AI MCP is an AI-augmented banking operations platform that exposes 20 domain tools over the **Model Context Protocol (MCP)**. Any MCP-compatible client (Claude Desktop, another Spring AI app, or a custom agent) can connect and invoke banking operations conversationally, while the same tools are also available via a built-in REST chat API backed by GPT-4o.

**Core goals:**
- Allow natural-language banking operations (initiate payment, check fraud, onboard customer) via AI orchestration
- Enforce strict financial safety rules: fraud check before every payment, immediate fund holds, no stack-trace leakage
- Provide a clean domain API (REST) alongside the MCP interface — same business logic, two access paths
- Maintain full audit trail of every action for compliance

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Application Framework | Spring Boot 4.0.3 |
| AI Framework | Spring AI 2.0.0-M2 |
| AI Model | OpenAI GPT-4o (temperature 0.1, max-tokens 2048) |
| MCP Server | `spring-ai-starter-mcp-server-webmvc` (SYNC/WebMVC) |
| MCP Client | `spring-ai-starter-mcp-client` (HTTP transport) |
| Build System | Gradle 9 (multi-project monorepo) |
| DB (dev/test) | H2 in-memory |
| DB (production) | PostgreSQL 16 |
| Caching | Redis 7 (docker-compose) |
| Tracing | OpenTelemetry → OTel Collector → Grafana Tempo (opt-in via `tracing` profile) |
| Log Aggregation | Loki + Promtail (opt-in via `tracing` profile) |
| Observability UI | Grafana (trace↔log linking, datasource auto-provisioning) |
| Metrics | Micrometer + Prometheus |
| ORM | Spring Data JPA / Hibernate |
| DTO Mapping | MapStruct |
| AOP | AspectJ (`@EnableAspectJAutoProxy`) |
| Security | Spring Security (API key, stateless) |
| Event Streaming | Apache Kafka 4.1.1 (KRaft, 3-node) + Spring Kafka |
| Correlation Tracing | `X-Correlation-ID` header → SLF4J MDC → Kafka message header |
| Containerization | Docker + Docker Compose |
| Frontend | React 18 + TypeScript + Vite |
| Test Coverage Enforcement | JaCoCo 0.8.14 (70% minimum) |

---

## 3. Module Architecture

### 3.1 Dependency Graph

```
banking-common           ← shared domain objects, exception hierarchy, utilities
      ↑
      ├── banking-events        ← Kafka event DTOs (pure records, no Spring, no JPA)
      ├── banking-notification  ← email/SMS/push notification logging + Kafka consumer
      ├── banking-onboarding    ← customer lifecycle, KYC, AML (5 MCP tools)
      ├── banking-account       ← accounts, balance, holds, limits (6 MCP tools)
      ├── banking-payment       ← NEFT/RTGS/IMPS/UPI/SWIFT payments (7 MCP tools)
      └── banking-fraud         ← rule-based fraud detection (2 MCP tools)
           ↑
banking-ai-gateway        ← Spring Boot entry point, ChatClient, MCP server, REST API, Kafka consumers (port 8080)
           ↑  [MCP protocol — spring-ai-starter-mcp-client]
banking-mcp-client        ← standalone MCP client, direct tool invocation, Kafka KYC consumer (port 8081)
```

`banking-ai-gateway` and `banking-mcp-client` are the two runnable artifacts (both carry the Spring Boot application plugin). All other modules produce plain library JARs.

### 3.2 Module Responsibilities

#### `banking-events`
- Pure Kafka event DTOs — Java records only; no Spring beans, no JPA, no domain logic
- Shared by every producer and consumer module; depends only on `jackson-annotations`
- Event types: `NotificationEvent` (sealed interface + Email/SMS/FraudAlert records), `PaymentStatusChangedEvent`, `KycStatusChangedEvent`, `AuditEvent`
- `EventMetadata` record carries `eventId` (UUID), `timestamp`, `source`, `correlationId`

#### `banking-common`
- `BaseEntity` — JPA `@MappedSuperclass` with audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`) and `@Version` for optimistic locking
- `ApiResponse<T>` — Record-based standardized response envelope used by all REST endpoints
- `Money` — Domain value object for monetary amounts with currency
- `BankingExceptions` — Typed exception hierarchy (see §6)
- `PagedResponse` — Pagination envelope

#### `banking-onboarding`
- Manages the full customer lifecycle from application to active status
- Entities: `Customer` (KYC status, risk category, onboarding status), `Address` (embedded), `CustomerDocument` (one-to-many for KYC docs)
- Enums: `KycStatus`, `OnboardingStatus`, `RiskCategory`, `Gender`, `IdDocumentType`, `EmploymentType`
- **5 MCP tools:** `get_customer_profile`, `get_customer_by_email`, `update_kyc_status`, `get_pending_kyc_customers`, `complete_customer_onboarding`

#### `banking-account`
- Manages bank accounts (SAVINGS, CURRENT, FIXED_DEPOSIT, SALARY, NRI)
- Key domain methods on `Account`: `debit()`, `credit()`, `placeHold()`, `releaseHold()`, `isActive()`, `hasSufficientFunds()`
- Statuses: ACTIVE, INACTIVE, BLOCKED, DORMANT, CLOSED
- **6 MCP tools:** `open_bank_account`, `get_account_details`, `get_account_balance`, `get_customer_accounts`, `check_sufficient_funds`, `block_account`

#### `banking-payment`
- Manages end-to-end payment lifecycle including fraud holds and reversals
- Payment types: NEFT, RTGS, IMPS, UPI, SWIFT, INTERNAL, STANDING_ORDER
- Statuses: INITIATED → PENDING_FRAUD_CHECK → (FRAUD_HOLD or PROCESSING) → COMPLETED/FAILED/REVERSED/CANCELLED
- **7 MCP tools:** `initiate_payment`, `process_payment`, `get_payment_status`, `get_payment_history`, `hold_payment_for_fraud`, `reverse_payment`, `get_daily_spending_summary`

#### `banking-fraud`
- Rule-based fraud scoring with 6 pluggable `@Component` rules
- Scores are summed; decision driven by aggregate:
  - `< 0.40` → APPROVE
  - `0.40–0.70` → HOLD_FOR_REVIEW
  - `≥ 0.70` → BLOCK
- Rules: `HighValueRule`, `VelocityRule`, `OffHoursRule`, `InternationalWireRule`, `RoundAmountRule`, `DailyLimitRule`
- **2 MCP tools:** `analyse_payment_fraud_risk`, `get_fraud_decision_guidance`

#### `banking-notification`
- Logs all domain events (onboarding, account, payment, fraud) via SLF4J
- `NotificationEventConsumer` — `@KafkaListener` on `banking.notifications` topic; routes to email/SMS/fraud-alert handlers
- Production replacement: Twilio (SMS), AWS SNS (push), SMTP (email)
- No MCP tools; invoked either directly by domain services or via Kafka consumer

#### `banking-ai-gateway`
- Spring Boot application, imports all domain modules
- Embeds MCP server (SYNC/WebMVC, port 8080)
- Runs `ChatClient` backed by GPT-4o with all 20 tools registered via `MethodToolCallbackProvider`
- Manages multi-turn conversation sessions (in-memory, max 1000 sessions, 50-message trim)
- Hosts REST API, Spring Security, AOP audit logging, global exception handler
- **Kafka consumers** (activated by `banking.kafka.enabled=true`): `PaymentStatusConsumer` (metrics/logging on `banking.payments.status`), `AuditEventConsumer` (persists to `audit_events` table from `banking.audit.trail`)
- **Kafka config** (`KafkaConfig`): creates 4 topics with DLT, `ExponentialBackOff` error handler with `DeadLetterPublishingRecoverer`

#### `banking-mcp-client`
- Standalone Spring Boot application (port 8081) — **does not import any domain modules**
- Connects to `banking-ai-gateway` over MCP protocol using `spring-ai-starter-mcp-client`
- Invokes the 20 banking tools directly via `McpSyncClient` — no AI model involved
- `BankingMcpClientService` — core tool invocation methods grouped by use case
- `ComplianceScheduler` — hourly KYC compliance check + 5-minute server health ping (toggle via `banking.compliance.kyc-check-enabled`)
- `KycStatusConsumer` — `@KafkaListener` on `banking.onboarding.kyc-status`; reacts to KYC events immediately (replaces polling when `kafka` profile active)
- `BankingMcpClientController` — REST API at `/api/mcp/**` for admin, compliance, and fraud-ops consumers

---

## 4. MCP Server & AI Orchestration

### 4.1 MCP Server Configuration

```yaml
spring.ai.mcp.server:
  enabled: true        # activates McpServerSseWebMvcAutoConfiguration
  name: banking-mcp-server
  version: 1.0.0
  type: SYNC           # API style: SYNC (blocking) vs ASYNC (reactive)
  protocol: SSE        # transport: activates /sse and /mcp/message endpoints
  capabilities:
    tool: true
```

The server exposes all 20 tools over the MCP protocol via two endpoints:

| Path | Protocol | Purpose |
|------|----------|---------|
| `GET /sse` | Server-Sent Events | Persistent connection — MCP Inspector / client connects here |
| `POST /mcp/message` | HTTP JSON-RPC 2.0 | Tool calls (`tools/list`, `tools/call`) |

These endpoints require **no API key** — they are open in `SecurityConfig` (`permitAll`) and skipped by `ApiKeyAuthFilter`. In production, secure them at the network boundary (VPC, mTLS) rather than via HTTP headers, since SSE clients cannot reliably send custom headers.

**MCP Inspector setup:** Transport = SSE, URL = `http://localhost:8080/sse`

### 4.2 Tool Registration

`BankingAiConfig` uses `MethodToolCallbackProvider` to reflect over the four `*McpTool` beans (`AccountMcpTool`, `PaymentMcpTool`, `FraudMcpTool`, `OnboardingMcpTool`), discovers `@Tool`-annotated methods, and registers them both with the `ChatClient` (for GPT-4o calls) and with the MCP server.

**AOP proxy unwrapping is required:** `AopProxyUtils.getSingletonTarget()` is called on each bean before passing it to `MethodToolCallbackProvider`, because reflection on a proxy does not find `@Tool` annotations on the real class.

### 4.3 MCP Client Configuration

`banking-mcp-client` configures its `McpSyncClient` via `application.yml`:

```yaml
spring:
  ai:
    mcp:
      client:
        connections:
          banking-server:
            url: http://localhost:8080   # banking-ai-gateway base URL
```

Spring AI auto-configures one `McpSyncClient` bean per connection entry. The client bypasses GPT-4o entirely and calls tools deterministically — suitable for scheduled jobs, admin ops, and integration test harnesses.

**MCP Client REST endpoints (`http://localhost:8081`):**

| Method | Path | Use Case |
|--------|------|----------|
| GET | `/api/mcp/tools` | List all 20 registered tools |
| POST | `/api/mcp/tools/{toolName}` | Invoke any tool (integration testing) |
| GET | `/api/mcp/compliance/kyc/pending` | Fetch KYC-pending customers |
| POST | `/api/mcp/compliance/kyc/{id}/approve` | Approve customer KYC |
| POST | `/api/mcp/admin/accounts/{id}/block` | Block an account (ops) |
| GET | `/api/mcp/admin/accounts/{id}/balance` | Account balance (admin) |
| GET | `/api/mcp/admin/accounts/{id}/spending-summary` | Daily spending (admin) |
| GET | `/api/mcp/fraud/payments/{id}/analyse` | Run fraud analysis |
| GET | `/api/mcp/fraud/payments/{id}/guidance` | Get APPROVE/HOLD/BLOCK guidance |
| POST | `/api/mcp/fraud/payments/{id}/hold` | Hold payment for fraud |

### 4.4 System Prompt Rules

The system prompt injected into every GPT-4o conversation enforces:
1. Fraud check (`analyse_payment_fraud_risk`) must run before every `process_payment`
2. Accounts must be ACTIVE before payments; check `get_account_details` first
3. Customer must have APPROVED KYC before account operations
4. FRAUD_HOLD and BLOCK decisions must be escalated to human compliance team
5. `block_account` calls must include an explicit reason in the audit log
6. RTGS/SWIFT payments require confirmation for amounts > ₹2 lakh
7. Velocity rule triggers (>5 transactions/hour) must be flagged to supervisor
8. Never expose internal IDs, stack traces, or system error messages to end users
9. Conversation history is trimmed at 50 messages; summarize when approaching limit
10. All sensitive operations require double-confirmation from the requesting agent

---

## 5. REST API

### 5.1 Authentication

All endpoints require `X-API-Key: {key}` header.
Optional `X-Client-ID: {name}` for audit log attribution.
Default demo key: `banking-demo-key-2024` (override via `BANKING_API_KEY` env var).

Public endpoints (no auth): `/actuator/health`, `/actuator/info`, `/sse`, `/mcp/**`

### 5.2 AI Chat Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/banking-ai/chat` | Multi-turn conversation (sessionId in body) |
| POST | `/api/v1/banking-ai/query` | Stateless single query |
| GET | `/api/v1/banking-ai/sessions/{sessionId}/history` | Retrieve conversation history |
| DELETE | `/api/v1/banking-ai/sessions/{sessionId}` | Clear session |
| GET | `/api/v1/banking-ai/sessions` | Session statistics |

**Chat request:**
```json
{
  "sessionId": "optional-uuid",
  "message": "Send ₹5000 from ACC-001 to ACC-002 and run a fraud check first"
}
```

### 5.3 Domain Endpoints

All return `ApiResponse<T>` wrapper:
```json
{
  "success": true,
  "data": { ... },
  "errorCode": null,
  "message": null,
  "timestamp": "2026-03-13T10:30:00Z"
}
```

- `AccountController` — account CRUD, balance inquiries
- `PaymentController` — payment lifecycle (initiate → process → complete/reverse)
- `CustomerOnboardingController` — customer onboarding, KYC workflow

### 5.4 Management Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Liveness & readiness |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |
| `/h2-console` | H2 database console (dev only) |

---

## 6. Exception Handling

All exceptions extend `BankingException` and carry an `errorCode` string and `httpStatus`. The `GlobalExceptionHandler` maps them to HTTP responses — no stack traces are ever sent to clients (`server.error.include-message: never`).

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `InsufficientFundsException` | 422 | `INSUFFICIENT_FUNDS` |
| `PaymentFraudHoldException` | 403 | `PAYMENT_FRAUD_HOLD` |
| `AccountInactiveException` | 422 | `ACCOUNT_INACTIVE` |
| `ConcurrentModificationException` | 409 | `CONCURRENT_MODIFICATION` |
| `KycNotApprovedException` | 403 | `KYC_NOT_APPROVED` |
| `ValidationException` | 400 | `VALIDATION_ERROR` |

**Rule:** Do not add generic catch-all handlers. Do not leak internal exception messages.

---

## 7. Key Design Patterns

### 7.1 Fund Holds
1. `initiate_payment` immediately places a hold on the source account (`Account.placeHold()`)
2. Hold released on `process_payment` completion, failure, or reversal
3. Prevents double-spending in asynchronous payment flows

### 7.2 Fraud Detection — Strategy Pattern
Each of the 6 fraud rules is a `@Component` implementing `FraudRule`. `FraudDetectionService` aggregates all rules via dependency injection and sums scores. Adding a new rule requires only adding a new `@Component` — no changes to the service (Open/Closed Principle).

### 7.3 DTO Mapping — MapStruct
All entity-to-DTO conversion is done via MapStruct `@Mapper` interfaces. Manual entity-to-DTO mapping code is forbidden. Compiler arg: `-Amapstruct.defaultComponentModel=spring`.

### 7.4 AOP Audit Logging
`AuditLogAspect` (`@Around`) intercepts all `BankingAiController.*` methods, logging caller identity (from `SecurityContextHolder`), method timing, and outcome. In production this should write to an immutable audit store (CloudTrail, dedicated audit DB).

### 7.5 Immutable DTOs — Java Records
All DTOs use Java records:
```java
public record AccountResponse(String accountId, String accountNumber, BigDecimal balance, ...) {}
```
Immutable, no boilerplate, compiler-enforced canonical equals/hashCode.

### 7.6 Optimistic Locking
All JPA entities carry `@Version`. Concurrent updates throw `ObjectOptimisticLockingFailureException`, mapped to `409 CONCURRENT_MODIFICATION`. No pessimistic locks are used.

### 7.7 Transactional Boundaries
Service layer methods carry `@Transactional`. Repositories are `@Transactional(readOnly = true)` by default. Domain modules do not call each other's repositories directly — they go through the service layer.

### 7.8 Session Management
`BankingAiService` maintains an in-memory `ConcurrentHashMap<String, List<Message>>`. Sessions are trimmed to 50 messages to respect GPT-4o token budgets. Maximum 1000 concurrent sessions (LRU eviction thereafter).

### 7.9 Cross-Module Coupling
Modules reference each other via string IDs (e.g., `customerId`, `accountId`), not JPA foreign keys. This keeps modules independently deployable and avoids cross-schema JPA joins.

### 7.10 Correlation ID Tracing
A single `X-Correlation-ID` value is established at the HTTP boundary by a highest-priority servlet filter — read from the request header or generated as a UUID if absent. It is:
1. Stored in SLF4J MDC (`traceId` key) so every log line in the request thread is automatically tagged via the log pattern `[%X{traceId}]`
2. Echoed back in the HTTP response header
3. Embedded in `EventMetadata.correlationId` of every Kafka event published by the service layer (same thread as the filter)
4. Stamped as a `X-Correlation-ID` Kafka message header by a producer interceptor, which runs synchronously on the calling thread before the record is handed off
5. Extracted by each Kafka consumer via `@Header` and restored to MDC for the duration of message processing — consumer log lines carry the same ID as the originating HTTP request

This gives a single traceable ID across REST logs, event payloads, Kafka message headers, and all consumer logs.

---

## 8. Data Layer

### 8.1 Databases

| Environment | Database | DDL |
|-------------|----------|-----|
| Dev / Test | H2 in-memory | `create-drop` (auto from JPA entities) |
| Production | PostgreSQL 16 | Migration scripts in `db/migration/` |

### 8.2 Schema Migrations

- `V1__initial_schema.sql` — Creates all tables and indexes
- `V2__seed_demo_data.sql` — Populates demo customers, accounts, payments for dev/test

Migrations run on startup. Do not modify existing migration files — add new versioned files.

### 8.3 Entity Conventions

- Extend `BaseEntity` for all persistent entities
- Use `@Version` (already in `BaseEntity`) — never suppress optimistic locking
- Use `@Column(nullable = false)` for required fields — rely on DB constraints, not only application validation
- Enum columns stored as `STRING` (`@Enumerated(EnumType.STRING)`)

---

## 9. Security

### 9.1 API Key Authentication
`ApiKeyAuthFilter` reads `X-API-Key` from the request header, validates it against `banking.security.api-key`, and sets a `UsernamePasswordAuthenticationToken` (`ROLE_API_USER`) in the `SecurityContextHolder`.

### 9.2 Session Policy
`SessionCreationPolicy.STATELESS` — no HTTP session is created or used. All state is passed per-request.

### 9.3 CSRF
Disabled for `/api/**`, `/h2-console/**`, `/sse`, and `/mcp/**` (API clients and MCP transport endpoints — no browser form submissions).

### 9.4 Headers
`X-Frame-Options: SAMEORIGIN` for H2 console. Standard Spring Security headers for all other endpoints.

### 9.5 Error Exposure
`server.error.include-message: never` — error responses never include exception messages, stack traces, or internal field names.

---

## 10. Observability

| Signal | Mechanism | Endpoint / Notes |
|--------|-----------|-----------------|
| Health | Spring Actuator | `/actuator/health` |
| Metrics | Micrometer | `/actuator/metrics` |
| Prometheus | Micrometer registry | `/actuator/prometheus` |
| Distributed Tracing | OpenTelemetry → Grafana Tempo | `http://localhost:3200` (opt-in via `--profile tracing`) |
| Log Aggregation | Loki + Promtail | `http://localhost:3100` (opt-in via `--profile tracing`) |
| Unified Observability UI | Grafana | `http://localhost:3000` (admin/admin; auto-provisioned Tempo + Loki + Prometheus datasources) |
| Audit Log | `AuditLogAspect` (SLF4J + Kafka) | Structured events on `banking.audit.trail`; log aggregator in production |
| Correlation ID | `X-Correlation-ID` header → MDC → Kafka header | Single ID across HTTP, logs, and async Kafka boundaries (see §7.10) |

Application name tag is attached to all Micrometer metrics: `management.metrics.tags.application: ${spring.application.name}`.

**Correlation ID header:** Pass `X-Correlation-ID: <id>` on any request to trace it end-to-end. The value appears in every log line (`[%X{traceId}]`), in the Kafka message header of every event published by that request, and in all consumer logs that process those events. If the header is absent a UUID is auto-generated and returned in the response.

---

## 11. Kafka Event Streaming

### 11.1 Overview

Kafka decouples notifications, audit trail, and compliance reactions from synchronous transactional paths. All Kafka functionality is feature-flagged via `banking.kafka.enabled` — the system runs fully without Kafka (graceful degradation).

### 11.2 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|-------|--------------|------------|-----------|---------|
| `banking.notifications` | `customerId` | 6 | 7 days | Email, SMS, fraud alert delivery |
| `banking.payments.status` | `sourceAccountId` | 12 | 90 days | Payment lifecycle events |
| `banking.audit.trail` | `caller` | 6 | 365 days | Immutable audit log |
| `banking.onboarding.kyc-status` | `customerId` | 3 | 30 days | KYC status change events |

Each topic has a corresponding `.DLT` (Dead Letter Topic) for failed messages.

### 11.3 Producer Pattern

Publishers use `@TransactionalEventListener(phase = AFTER_COMMIT)` — Kafka messages are sent only after the DB transaction commits. This prevents phantom events on rollback. All publishers are `@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")`.

| Module | Publisher | Events Published |
|--------|-----------|-----------------|
| `banking-payment` | `PaymentEventPublisher` | `PaymentStatusChangedEvent`, `NotificationEvent` |
| `banking-onboarding` | `OnboardingEventPublisher` | `KycStatusChangedEvent`, `NotificationEvent` |
| `banking-account` | `AccountEventPublisher` | `NotificationEvent` |
| `banking-ai-gateway` | `AuditLogAspect` (enhanced) | `AuditEvent` |

### 11.4 Consumer Design

| Consumer | Module | Group ID | Topic |
|----------|--------|----------|-------|
| `NotificationEventConsumer` | `banking-notification` | `notification-delivery-group` | `banking.notifications` |
| `PaymentStatusConsumer` | `banking-ai-gateway` | `payment-analytics-group` | `banking.payments.status` |
| `AuditEventConsumer` | `banking-ai-gateway` | `audit-persistence-group` | `banking.audit.trail` |
| `KycStatusConsumer` | `banking-mcp-client` | `compliance-kyc-group` | `banking.onboarding.kyc-status` |

### 11.5 Error Handling

- **Retries:** `ExponentialBackOff` (1s → 2s → 4s), max 3 attempts (5 for payment events)
- **DLT:** Failed messages routed to `{topic}.DLT` via `DeadLetterPublishingRecoverer`
- **Idempotency:** Every event carries a UUID `eventId`; audit consumer enforces DB-level uniqueness
- **Producer:** `enable.idempotence=true`, `acks=all`
- **Fallback:** Kafka producer failures are logged locally — never cascade into transaction failures

### 11.6 Activation

```bash
# Profile-based (recommended for dev)
SPRING_PROFILES_ACTIVE=dev,kafka ./gradlew :banking-ai-gateway:bootRun

# Environment variable
BANKING_KAFKA_ENABLED=true ./gradlew :banking-ai-gateway:bootRun
```

---

## 12. Testing

### 12.1 Coverage Requirement

JaCoCo enforces **70% minimum line and branch coverage** on every `./gradlew build`. The build fails if any module drops below this threshold.

**Excluded from coverage:** `**/domain/**`, `**/dto/**`, `**/config/**`, `**/exception/**`, `**/events/**`

### 12.2 Test Layers

| Layer | Annotation | Scope |
|-------|-----------|-------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Service, rule, mapper logic |
| Web slice | `@WebMvcTest` | Controller request/response mapping |
| Data slice | `@DataJpaTest` | Repository queries, entity constraints |
| Integration | `@SpringBootTest` | Full context, H2 in-memory |
| MCP tool | `@SpringBootTest` | Tool input/output, tool-to-service wiring |

### 12.3 Commands

```bash
./gradlew test                               # All tests
./gradlew :banking-account:test              # Single module
./gradlew :banking-account:test --tests "com.banking.account.mcp.AccountMcpToolTest"
./gradlew test jacocoRootReport              # Coverage report → build/reports/jacoco/root/html/index.html
```

---

## 13. Build & Packaging

### 13.1 Gradle Monorepo

Root `build.gradle` configures:
- Java 25 toolchain
- Common plugin block (Spring Boot, Spring Dependency Management, JaCoCo) applied to all subprojects
- MapStruct compiler args (`-Amapstruct.defaultComponentModel=spring`) on all subprojects
- JaCoCo 0.8.14 with 70% minimum coverage check
- Aggregate JaCoCo report via `jacocoRootReport` task

`settings.gradle` lists all module includes and configures Maven repositories (mavenCentral, Spring milestone, Spring snapshot).

### 13.2 Artifacts

Two runnable fat JARs are produced via the Spring Boot application plugin:
- `banking-ai-gateway` — MCP server + ChatClient + REST API
- `banking-mcp-client` — MCP client + compliance scheduler + admin REST API

All other modules produce plain library JARs consumed by the gateway.

### 13.3 Docker

**Multi-stage Dockerfile:**
1. `builder` stage — Gradle build, outputs fat JAR
2. `runtime` stage — `eclipse-temurin:21-jre-alpine`, non-root user `banking:banking`, JVM tuned for containers (75% RAM, G1GC, string dedup)

**docker-compose.yml services:**
- `banking-ai-gateway` — application on port 8080
- `postgres:16-alpine` — `bankingdb`, persistent volume
- `redis:7-alpine` — session/cache on port 6379
- `otel-collector` — OpenTelemetry Collector (OTLP receiver, opt-in `tracing` profile)
- `tempo` — Grafana Tempo trace storage (opt-in `tracing` profile)
- `loki` — Grafana Loki log aggregation (opt-in `tracing` profile)
- `promtail` — Log shipper to Loki (opt-in `tracing` profile)
- `prometheus` — Metrics scraping (opt-in `tracing` profile)
- `grafana` — Unified observability UI on port 3000 (opt-in `tracing` profile)
- `kafka-1`, `kafka-2`, `kafka-3` — Apache Kafka 4.1.1 KRaft 3-node cluster (ports 9092, 9094, 9096 external)
- `kafka-ui` — Kafka UI (`provectuslabs/kafka-ui`) on port 8090

---

## 14. Frontend

React 18 / TypeScript / Vite SPA in `banking-ai-frontend/`.

### Pages

| Route | Purpose |
|-------|---------|
| `/dashboard` | Overview stats |
| `/onboarding` | Customer KYC workflow |
| `/accounts` | Account listing & detail |
| `/payments` | Payment history & initiation |
| `/ai-assistant` | Chat interface to MCP tools |

### API Client

Axios instance in `src/api/client.ts` with request/response interceptors (injects `X-API-Key`).

Modules: `api/ai.ts`, `api/onboarding.ts`, `api/accounts.ts`, `api/payments.ts`

Types in `api/types.ts` are aligned with backend Java records.

### Development

```bash
cd banking-ai-frontend
npm install
npm run dev      # Vite dev server (proxies /api to localhost:8080)
npm run build    # Production build
npm run lint     # ESLint
```

---

## 15. Environment Variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `OPENAI_API_KEY` | Yes (prod) | — | GPT-4o access |
| `BANKING_API_KEY` | No | `banking-demo-key-2024` | REST API authentication |
| `SPRING_PROFILES_ACTIVE` | No | `default` | `dev` loads demo data; `kafka` activates Kafka |
| `SPRING_DATASOURCE_URL` | No | H2 in-memory | Override for PostgreSQL in prod |
| `BANKING_KAFKA_ENABLED` | No | `false` | Activate Kafka publishers/consumers (or use `kafka` profile) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092,localhost:9094,localhost:9096` | Kafka broker addresses |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | `http://localhost:4318` | OTel Collector OTLP endpoint; only used when `tracing` profile active |
| `TRACING_SAMPLING_PROBABILITY` | No | `1.0` | Trace sampling rate 0.0–1.0; set to `0.1` for production |

---

## 16. Quick Start

```bash
# Build all modules
./gradlew clean build

# Run gateway in dev mode (loads demo data)
cd banking-ai-gateway
SPRING_PROFILES_ACTIVE=dev OPENAI_API_KEY=sk-... ../gradlew bootRun

# Run gateway with Kafka enabled (requires Kafka running on localhost:9092,9094,9096)
SPRING_PROFILES_ACTIVE=dev,kafka OPENAI_API_KEY=sk-... ./gradlew :banking-ai-gateway:bootRun

# Run MCP client (requires gateway running on :8080)
cd banking-mcp-client && ../gradlew bootRun

# Run MCP client with Kafka KYC consumer
SPRING_PROFILES_ACTIVE=kafka ./gradlew :banking-mcp-client:bootRun

# Run full production stack (includes Kafka cluster + Kafka UI)
export OPENAI_API_KEY=sk-...
docker-compose up

# Frontend dev server
cd banking-ai-frontend && npm install && npm run dev

# Run tests with coverage report
./gradlew test jacocoRootReport
```

**Dev endpoints:**
- Chat API: `http://localhost:8080/api/v1/banking-ai/chat`
- MCP Client API: `http://localhost:8081/api/mcp/tools`
- H2 Console: `http://localhost:8080/h2-console` (user: sa, no password)
- Health (gateway): `http://localhost:8080/actuator/health`
- Health (client): `http://localhost:8081/actuator/health`
- Kafka UI: `http://localhost:8090` (docker-compose `--profile tools`)
- Grafana: `http://localhost:3000` (admin/admin; docker-compose `--profile tracing`)
- Tempo: `http://localhost:3200` (docker-compose `--profile tracing`)
- Loki: `http://localhost:3100` (docker-compose `--profile tracing`)

---

## 17. Extension Points

| What to extend | How |
|---------------|-----|
| Add a new fraud rule | Create a `@Component` implementing `FraudRule`; it is auto-discovered by `FraudDetectionService` |
| Add a new MCP tool | Add a `@Tool`-annotated method to the relevant `*McpTool` class; it is auto-discovered at startup |
| Add a new domain module | Create a Gradle subproject, add it to `settings.gradle`, add the dependency to `banking-ai-gateway/build.gradle` |
| Add a new MCP client use case | Add a method to `BankingMcpClientService` calling `callTool()`; expose via `BankingMcpClientController` if needed |
| Use a different AI model | Wire a new `ChatModel` bean with `ToolCallbackProvider mcpTools` from the client starter — tools are model-agnostic |
| Add a new Kafka event type | Add a record to `banking-events`; add a publisher + `@TransactionalEventListener` in the producing module; add a `@KafkaListener` consumer gated by `@ConditionalOnProperty` |
| Replace notification provider | Swap `NotificationService` implementation; notification consumers receive events via Kafka (`banking.notifications` topic) |
| Persist sessions | Replace in-memory map in `BankingAiService` with Redis-backed store |
| Add a new payment type | Add an enum value to `PaymentType`; extend the fraud rule logic if needed |
