---
paths:
  - "banking-ai-gateway/**"
  - "banking-mcp-client/**"
  - "**/config/BankingAiConfig.java"
  - "**/service/BankingAiService.java"
  - "**/controller/BankingAiController.java"
---

# AI Orchestration Rules

## ChatClient Usage
- A single `ChatClient` instance is configured in `BankingAiConfig` — never create ad-hoc `ChatClient` instances in controllers or services
- System prompt must define the AI's role, constraints, and output format — always include it; never rely on the default system prompt
- Tool callbacks are registered once at startup via `MethodToolCallbackProvider` — do not register tools dynamically at request time

## Tool Registration — AOP Proxy Unwrapping (Critical)
`MethodToolCallbackProvider` reads `@Tool` annotations via reflection on the real class, not the Spring proxy.
Always unwrap before registration:
```java
MethodToolCallbackProvider.builder()
    .toolObjects(
        AopProxyUtils.getSingletonTarget(accountMcpTool),
        AopProxyUtils.getSingletonTarget(paymentMcpTool),
        AopProxyUtils.getSingletonTarget(fraudMcpTool),
        AopProxyUtils.getSingletonTarget(onboardingMcpTool)
    )
    .build();
```
If a tool is silently absent from the model's tool list, missing unwrapping is the first thing to check.

## Session Management
- Conversation sessions are stored in-memory: max 1000 sessions, trim at 50 messages per session
- Never persist raw conversation history to the database — it may contain sensitive AI reasoning
- Session IDs come from the client — validate they are non-blank UUIDs before use
- Do not share session state across requests from different users

## Prompt Injection Prevention
- Never concatenate raw user input into the system prompt string
- User messages go through the `ChatClient` user message channel only — not the system channel
- Tool arguments provided by the AI model must be validated the same as user input — the LLM can hallucinate values
- If a tool argument looks like a prompt injection attempt (e.g., "ignore previous instructions"), reject it

## Tool Result Safety
- Tool results are returned to the model for reasoning — never include internal stack traces, SQL errors, or raw exception messages in tool return values
- Strip or sanitize error messages before returning `ApiResponse.error(...)` from tool methods
- Financial amounts in tool results must use `Money` type or formatted strings — never raw `double`

## MCP Server Configuration
The MCP server is configured in `application.yml` under `spring.ai.mcp.server`:
- `type: SYNC` — blocking API style; do not change to ASYNC without migrating all tool methods to reactive
- `protocol: SSE` — transport registers `/sse` and `/mcp/message`; these endpoints must remain unauthenticated
- `capabilities.tool: true` — required for tool invocation; never set to false

## MCP Client (`banking-mcp-client`) Rules
- The MCP client uses `McpSyncClient` (auto-configured) — never create a raw HTTP client to call tool endpoints
- Tool invocation via `BankingMcpClientService.invokeTool(name, args)` is the canonical pattern
- The client must not replicate business logic — it is an operator/admin interface only
- `ComplianceScheduler` is disabled by default (`banking.compliance.kyc-check-enabled=false`) — document clearly when enabling in a non-dev environment

## Model Selection
- Default model: `gpt-4o` (configured in `application.yml`)
- Do not hardcode model names in Java code — always reference the config property `spring.ai.openai.chat.options.model`
- If adding a new AI feature, use the same configured model — do not introduce a second model without architectural discussion

## What NOT To Do
- Do not bypass the `ChatClient` to call the OpenAI API directly — Spring AI handles retries, token limits, and tool execution
- Do not expose the raw AI response to the end client without validation — parse and validate the structure first
- Do not add `@Tool` methods that perform write operations without idempotency checks — the model may call a tool multiple times
- Do not return model reasoning/chain-of-thought in the API response — return only the final structured result
