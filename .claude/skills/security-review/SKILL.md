---
name: security-review
description: Run a security and PII checklist on changed code before committing or raising a PR. Use when the user asks for a security review or before merging changes that touch payments, customer data, auth, or logging.
user-invocable: true
argument-hint: "[file-or-module]"
---

Perform a security and PII review of the changed code. If `$ARGUMENTS` specifies a file or module, focus there; otherwise review all recently modified files.

## Step 1 — Identify the files to review

If `$ARGUMENTS` is given, read those files. Otherwise:
- Run `git diff --name-only HEAD` to find modified files
- Focus on Java source files (skip `*.gradle`, `*.yml`, `*.md` unless they contain secrets)

## Step 2 — PII Logging Check

Scan every `log.info`, `log.warn`, `log.error`, `log.debug` statement.

**Forbidden in log statements:**
- Customer name (`customer.getName()`, `name`, `firstName`, `lastName`)
- Email address
- Phone number
- Date of birth
- SSN / national ID
- Street address
- Full account number (last 4 only is permitted: `****1234`)
- Password, PIN, token, secret

**Permitted in log statements:**
- `customerId` (UUID)
- `accountId` (UUID)
- `paymentId` (UUID)
- `correlationId` / `traceId`
- Status codes and enum values
- Amounts (non-PII)
- Error codes

**Correct pattern:**
```java
log.info("[{}] Payment initiated for account ****{}", MDC.get("traceId"),
    accountNumber.substring(accountNumber.length() - 4));
```

**Report every violation** with file name, line number, and what PII is exposed.

## Step 3 — Secret & Credential Check

Scan for hardcoded secrets:
- API keys, passwords, tokens as string literals
- `OPENAI_API_KEY`, `BANKING_API_KEY` appearing as literal values (not `${...}` references)
- JDBC connection strings with embedded passwords
- Any string matching patterns like `sk-`, `Bearer `, `password=`, `secret=`

All secrets must come from environment variables — `${ENV_VAR}` in `application.yml` only.

## Step 4 — Exception Handling Check

Scan for dangerous exception patterns:

| Anti-pattern | Why it's wrong |
|---|---|
| `catch (Exception e)` generic handler | Masks bugs, leaks unexpected errors |
| `e.getMessage()` in HTTP response body | May expose internal paths, SQL, or PII |
| Stack traces in API responses | Exposes implementation details |
| Silently swallowed exceptions (`} catch(e) {}`) | Hides failures silently |

All exception handling must use the typed hierarchy from `banking-common`. Report violations with location.

## Step 5 — Input Validation Check

Scan entry points: REST controllers and `*McpTool` methods.

Verify:
- `@Validated` is present on controller class or method
- Request DTOs have `@NotNull`, `@NotBlank`, `@Positive` on required fields
- UUIDs validated as UUID format before service call
- Monetary amounts validated as positive before service call
- MCP tool `@ToolParam` methods validate inputs (LLMs can hallucinate invalid values)

Flag any endpoint or tool method that accepts user/AI input without validation.

## Step 6 — Financial Integrity Check

For any code touching payments, balances, or holds:

- [ ] Monetary values use `Money` type — never `double`, `float`, or raw `BigDecimal`
- [ ] Fund holds placed **before** any external call (payment initiation pattern)
- [ ] Holds released in **both** success and failure paths
- [ ] `@Version Long version` present on all entities (optimistic locking)
- [ ] Payment creation checks idempotency key before creating new record
- [ ] `@Transactional` on all service write methods

## Step 7 — API Security Check

- [ ] No `@CrossOrigin("*")` added to controllers — CORS configured centrally
- [ ] No `permitAll()` added for business endpoints — only `/sse`, `/mcp/message`, `/h2-console` (dev only) are public
- [ ] H2 console config not present in production profile
- [ ] No secrets in `application.yml` committed — all `${ENV_VAR}` references

## Step 8 — Correlation ID & Audit Check

- [ ] All log statements in request-scoped code include `MDC.get("traceId")`
- [ ] `CorrelationIdFilter` not removed or bypassed
- [ ] Kafka consumer methods restore `MDC["traceId"]` from headers and clear in `finally`
- [ ] State-changing operations (account creation, payments, KYC decisions) are covered by `AuditLogAspect` — not duplicating audit logging in service layer

## Step 9 — Report

Present findings grouped by severity:

### 🔴 Critical (must fix before merge)
- Hardcoded secrets
- PII in logs
- Stack traces in HTTP responses
- Missing fund holds

### 🟡 Warning (should fix)
- Missing input validation
- Generic exception handlers
- Missing `@Transactional` on write methods

### 🟢 Informational (good to know)
- Coverage gaps
- Missing correlation ID in logs
- Suggested improvements

If no issues found, confirm: "No security or PII issues found in reviewed files."
