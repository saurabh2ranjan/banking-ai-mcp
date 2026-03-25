# banking-mcp-client — Module Rules

## Purpose
Standalone MCP client (port 8081) for compliance automation, admin operations, and fraud ops. Connects to the gateway MCP server over the MCP protocol — does NOT import domain module classes directly.

## Hard Architectural Constraint
- This module must NOT have compile-time dependencies on domain modules (`banking-account`, `banking-payment`, etc.)
- All tool invocations go through `McpSyncClient` (Spring AI auto-configured) over the MCP protocol
- Tool arguments are constructed as `Map<String, Object>` — not typed domain DTOs
- Responses are parsed from the MCP tool result JSON — not cast to domain types

## Tool Invocation Pattern
```java
// Correct — generic tool invocation via MCP protocol
public Object invokeTool(String toolName, Map<String, Object> args) {
    return mcpSyncClient.callTool(
        new CallToolRequest(toolName, args)
    ).content();
}

// Wrong — importing domain classes breaks module isolation
AccountResponse account = accountService.getAccount(id); // NOT allowed here
```

## ComplianceScheduler Rules
- Disabled by default: `banking.compliance.kyc-check-enabled=false`
- When enabled: runs KYC checks hourly and server health pings every 5 minutes
- Schedule expressions come from config (`banking.compliance.kyc-check-cron`) — never hardcoded
- Log every scheduled execution with its correlationId (generate a UUID per run)
- Scheduler must handle `McpSyncClient` connection failures gracefully — log and continue, do not crash

## REST API Rules
- `POST /api/mcp/tools/{toolName}` — generic tool invocation endpoint (used for integration testing)
- Request body: `{ "args": { ... } }` — always a map
- Response: the raw tool result wrapped in `ApiResponse`
- Never add domain-specific endpoints to this module (e.g., `/api/accounts/{id}`) — use the generic `/tools/{toolName}` pattern

## Kafka in This Module
- KYC result consumer is gated by `banking.kafka.enabled=true` (kafka Spring profile)
- Consumer restores MDC from Kafka headers (correlationId propagation)
- KYC consumer uses event types from `banking-events` module — this is the only cross-module import allowed

## Testing Rules
- Unit test `BankingMcpClientService` by mocking `McpSyncClient` — verify correct tool names and arg maps
- Integration test the scheduler with `@SpringBootTest` — verify it calls the expected tools
- Do not write tests that require a running gateway — mock the MCP client
- Test both Kafka-enabled and Kafka-disabled profiles

## What NOT To Do
- Do not implement business logic (fraud scoring, KYC decisions) in this module
- Do not add `@Tool` annotations here — this module is a client, not a server
- Do not hardcode tool names as strings inline — define them as constants in a `ToolNames` class
- Do not expose internal MCP client errors directly in the REST API response — wrap and sanitize
