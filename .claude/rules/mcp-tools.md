---
paths:
  - "**/mcp/**"
  - "**/*McpTool.java"
  - "**/config/BankingAiConfig.java"
---

# MCP Tool Design Rules

## Tool Class Contract
- One `*McpTool` class per domain module — never split a domain across multiple tool classes
- Maximum 10 `@Tool`-annotated methods per class — if you exceed this, the domain needs subdivision
- The tool class is a thin orchestration layer — it calls the service, transforms the response, handles errors
- Never put business logic in a `*McpTool` class — it belongs in the `*Service`
- Tool classes must be Spring `@Component` beans registered via `MethodToolCallbackProvider` in `BankingAiConfig`

## @Tool Description Standard (Critical for AI Quality)
Every `@Tool` annotation must follow this template:
```
"{Action verb} {what it operates on}. {Key input required}. Returns {what the caller gets back}. {Side effect if any.}"
```

Examples:
```java
// Correct — specific, actionable, tells the model what it gets back
@Tool(description = "Retrieve the current balance and account status for a given account ID. " +
    "Requires a valid account UUID. Returns account details including available balance, " +
    "currency, account type, and current status.")

// Correct — captures side effect
@Tool(description = "Initiate a payment transfer between two accounts. " +
    "Places an immediate fund hold on the source account to prevent double-spending. " +
    "Returns a payment ID and PENDING status; settlement is asynchronous.")

// Wrong — vague, no return info, no side effect mention
@Tool(description = "Get account info")
@Tool(description = "Process payment")
```

## @ToolParam Description Standard
Every parameter must have a description that explains format, valid values, and purpose:
```java
// Correct
@ToolParam(description = "The unique account identifier (UUID format, e.g. '550e8400-e29b-41d4-a716-446655440000')")
String accountId,

@ToolParam(description = "Payment amount in the account's base currency. Must be positive and not exceed the daily transfer limit.")
BigDecimal amount,

// Wrong
@ToolParam(description = "account id")
String accountId,
```

## Return Type Contract
- All `@Tool` methods return either `ApiResponse<T>` from `banking-common` or a well-defined DTO record
- Never return `void`, raw `String`, or untyped `Object` from a tool method
- Never let exceptions propagate out of a `@Tool` method — catch domain exceptions and return error `ApiResponse`
- The model needs structured responses to make decisions — free-form strings break AI orchestration

```java
// Correct
public ApiResponse<AccountResponse> getAccountBalance(String accountId) {
    try {
        return ApiResponse.success(accountService.getAccount(accountId));
    } catch (AccountNotFoundException e) {
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }
}

// Wrong — exception leaks to Spring AI, which produces unhelpful model errors
public AccountResponse getAccountBalance(String accountId) {
    return accountService.getAccount(accountId); // throws if not found
}
```

## Tool Naming Convention
- Tool method names: `camelCase`, `verbNoun` pattern
- Verb vocabulary: `get`, `list`, `create`, `update`, `initiate`, `approve`, `reject`, `check`, `analyze`, `block`
- Be precise: `initiatePayment` not `pay`; `getAccountBalance` not `balance`; `analyzeTransactionFraud` not `fraud`

## Tool Input Validation
- Validate all inputs at the start of every tool method — before calling the service
- Null/blank checks on string IDs; positive checks on amounts; enum validation on type parameters
- Return `ApiResponse.error(...)` immediately on invalid input — do not propagate to service layer

## AOP Proxy Requirement
- When registering tools via `MethodToolCallbackProvider`, always unwrap AOP proxies:
  ```java
  AopProxyUtils.getSingletonTarget(mcpToolBean)
  ```
  Without this, `@Tool` annotations are invisible to reflection and tools are silently dropped.

## What NOT To Do
- Do not add `@Transactional` to tool methods — transactions belong in the service layer
- Do not call repositories directly from tool methods — always go through the service
- Do not add AI/LLM logic inside tool methods — tools are deterministic functions, not AI agents
- Do not use overloaded method names for tools — each tool name must be unique within the MCP server
- Do not expose internal entity field names in tool descriptions — use business language
