---
name: add-mcp-tool
description: Add a new @Tool method to an existing MCP tool class. Use when extending an existing domain (account, payment, onboarding, fraud) with a new AI-callable operation.
user-invocable: true
argument-hint: "<domain> <tool-name> <description>"
---

Add a new `@Tool` method to the `$0` domain MCP tool class. The tool name is `$1` and it should: $2

## Step 1 — Locate the MCP tool class

| Domain | File |
|---|---|
| account | `banking-account/src/main/java/com/banking/account/mcp/AccountMcpTool.java` |
| payment | `banking-payment/src/main/java/com/banking/payment/mcp/PaymentMcpTool.java` |
| onboarding | `banking-onboarding/src/main/java/com/banking/onboarding/mcp/OnboardingMcpTool.java` |
| fraud | `banking-fraud/src/main/java/com/banking/fraud/mcp/FraudMcpTool.java` |

Read the existing file first to understand the existing pattern before writing anything.

## Step 2 — Check if a service method is needed

If the operation requires data access or business logic:
- Add a method to the corresponding `*Service.java` in the `service/` package
- Service method must be `@Transactional` (write) or `@Transactional(readOnly = true)` (read)
- Throw typed exceptions from `banking-common` — never raw `RuntimeException`

If the operation is purely structural (e.g., formatting, routing), it can live in the MCP tool method itself.

## Step 3 — Write the @Tool method

### Method signature template

```java
@Tool(
    name = "$1",
    description = """
        [One sentence: what it does]
        [When to call it vs similar tools]
        [What it returns on success]
        [What errors can occur]
        """
)
public Map<String, Object> $1(
    @ToolParam(description = "...") String param1,
    @ToolParam(description = "...") @Nullable String optionalParam
) {
    // implementation
}
```

### Rules for the description
- Start with a verb: "Retrieves...", "Creates...", "Cancels..."
- State the preconditions (e.g., "Requires KYC to be VERIFIED")
- State what is returned on success
- List known error conditions (e.g., "Returns error if account is BLOCKED")
- Be specific enough that an LLM can decide when to call this vs similar tools

### Rules for the method body

**Always:**
- Validate inputs at the boundary — UUID format, positive amounts, non-blank strings
- Use `Money` type for monetary amounts — never `double` or `BigDecimal`
- Log with correlation ID from MDC: `log.info("[{}] ...", MDC.get("traceId"), id)`
- Return a `Map<String, Object>` with a `"status"` key (`"success"` or `"error"`) and relevant data

**Never:**
- Add `@Transactional` — it belongs on the service, not here
- Log PII (name, email, phone, SSN, DOB, address) — only IDs are safe
- Let domain exceptions propagate — catch them and return structured error maps
- Call another domain's service directly — use events or MCP tools

### Error handling pattern

```java
try {
    var result = service.doSomething(id);
    return Map.of(
        "status", "success",
        "data", mapper.toResponse(result)
    );
} catch (AccountNotFoundException e) {
    return Map.of("status", "error", "message", e.getMessage(), "code", "ACCOUNT_NOT_FOUND");
} catch (InsufficientFundsException e) {
    return Map.of("status", "error", "message", e.getMessage(), "code", "INSUFFICIENT_FUNDS");
}
```

## Step 4 — Verify tool registration

The gateway's `BankingAiConfig` uses `MethodToolCallbackProvider` which auto-discovers `@Tool` methods. **No manual registration needed** as long as:
- The method is in the correct `*McpTool` class that is already registered
- The `*McpTool` class is a Spring `@Component`
- `AopProxyUtils.getSingletonTarget(bean)` is used in `BankingAiConfig` (it already is)

If you created a **new** `*McpTool` class, you must add it to `BankingAiConfig`.

## Step 5 — Write the test

File: `src/test/java/com/banking/$0/mcp/$0McpToolTest.java` (already exists — add to it)

Test method naming: `toolName_whenCondition_thenExpectedOutcome`

```java
@Test
void $1_whenValidInput_thenReturnsSuccess() {
    // given
    when(service.doSomething(any())).thenReturn(mockResult);

    // when
    var result = mcpTool.$1(validInput);

    // then
    assertThat(result.get("status")).isEqualTo("success");
}

@Test
void $1_whenNotFound_thenReturnsError() {
    // given
    when(service.doSomething(any())).thenThrow(new AccountNotFoundException("ACC-001"));

    // when
    var result = mcpTool.$1("ACC-001");

    // then
    assertThat(result.get("status")).isEqualTo("error");
    assertThat(result.get("code")).isEqualTo("ACCOUNT_NOT_FOUND");
}
```

## Step 6 — Verify

```bash
./gradlew :banking-$0:test
```

Build must pass. If coverage drops below 70%, add more test cases.
