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

**Runtime endpoints (dev):**
- Gateway API: `http://localhost:8080`
- MCP Client API: `http://localhost:8081/api/mcp`
- H2 Console: `http://localhost:8080/h2-console`
- Health: `http://localhost:8080/actuator/health`
- MCP SSE (Inspector): `http://localhost:8080/sse`
- MCP Messages: `http://localhost:8080/mcp/message`

**Running MCP client alongside gateway:**
```bash
# Terminal 1 — MCP server (gateway)
cd banking-ai-gateway && SPRING_PROFILES_ACTIVE=dev OPENAI_API_KEY=sk-... ../gradlew bootRun

# Terminal 2 — MCP client
cd banking-mcp-client && ../gradlew bootRun
```

## Architecture

**Stack:** Spring Boot 4.0.3 · Java 25 · Spring AI 2.0.0-M2 · Gradle monorepo

### Module Dependency Graph

```
banking-common  (BaseEntity, Money, ApiResponse, exception hierarchy)
    ↑
    ├── banking-notification  (email/SMS/push events)
    ├── banking-onboarding    (customer lifecycle, KYC, AML — 5 MCP tools)
    ├── banking-account       (accounts, balance, limits, holds — 6 MCP tools)
    ├── banking-payment       (NEFT/RTGS/IMPS/UPI/SWIFT — 7 MCP tools)
    └── banking-fraud         (rule-based fraud detection — 2 MCP tools)
         ↑
banking-ai-gateway  (Spring Boot entry point — MCP server, ChatClient, REST API — port 8080)
         ↑  [MCP protocol]
banking-mcp-client  (standalone MCP client — direct tool invocation, no AI — port 8081)
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

This exposes the 20 banking tools over the MCP protocol. MCP endpoints (no API key required):
- `GET  http://localhost:8080/sse`          — SSE connection (Inspector/client connects here)
- `POST http://localhost:8080/mcp/message`  — JSON-RPC tool calls

Any MCP-compatible client (MCP Inspector, Claude Desktop, `banking-mcp-client`) can connect and invoke tools directly — in addition to the built-in REST chat endpoint.

**Tool registration** (`BankingAiConfig`): `MethodToolCallbackProvider` reflects over all four `*McpTool` beans at startup, discovers `@Tool`-annotated methods, and registers them both with the `ChatClient` (for internal GPT-4o calls) and with the MCP server (for external clients). AOP proxy unwrapping (`AopProxyUtils.getSingletonTarget`) is required because `MethodToolCallbackProvider` reads annotations via reflection on the real class, not the proxy.

### MCP Client (`banking-mcp-client`)

A standalone Spring Boot app (`spring-ai-starter-mcp-client`, port 8081) that connects to the gateway's MCP server and invokes tools **directly without AI orchestration**. Use cases:

- **Compliance automation:** `ComplianceScheduler` runs hourly KYC checks and 5-minute server health pings
- **Admin/Ops:** block accounts, check balances, query spending summaries via REST
- **Fraud ops:** trigger fraud analysis and hold payments programmatically
- **Generic invocation:** `POST /api/mcp/tools/{toolName}` — invoke any registered tool by name (integration testing)

`BankingMcpClientService` injects the auto-configured `McpSyncClient` bean; `ComplianceScheduler` is enabled via `banking.compliance.kyc-check-enabled=true` in `application.yml` (disabled by default).

### How AI Orchestration Works

`banking-ai-gateway` runs a Spring AI `ChatClient` backed by GPT-4o. All domain modules expose `@Tool`-annotated methods (via `MethodToolCallbackProvider`), which Spring AI auto-discovers and makes available to the model. The gateway manages conversation sessions in-memory (max 1000 sessions, 50-message trim).

### MCP Tool Classes

Each domain module has a single `*McpTool` class (e.g., `AccountMcpTool`, `PaymentMcpTool`) that holds all `@Tool` methods for that domain. These are registered as Spring beans and scanned by the gateway.

### Key Patterns

- **Fund Holds:** `initiatePayment` places an immediate hold on funds; the hold is released on complete or fail — prevents double-spending.
- **Fraud Engine:** Six `@Component` rule classes are scored and summed. Adding a new rule = add a new component implementing the rule interface.
- **AOP Audit Logging:** `AuditLogAspect` in the gateway intercepts all controller calls, recording caller, timing, and outcome.
- **Exception Hierarchy:** Typed exceptions in `banking-common` map to HTTP status codes — do not add generic catch-all handlers or leak stack traces.
- **DTO Mapping:** MapStruct `@Mapper` interfaces — do not write manual entity-to-DTO conversion code.

### Database

- **Dev/Test:** H2 in-memory; schema auto-created from JPA entities
- **Production:** PostgreSQL 16 (via docker-compose)
- Entities use `@Version` for optimistic locking; service methods use `@Transactional`

### Code Coverage

JaCoCo enforces **70% minimum** line/branch coverage. Tests must maintain this threshold or `./gradlew build` will fail.
