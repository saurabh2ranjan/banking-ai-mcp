# Domain Invariants & Module Boundary Rules

## Module Dependency Graph (Enforce Strictly)
```
banking-common   ← no dependencies on other banking modules
banking-events   ← depends on: banking-common only
banking-account  ← depends on: banking-common
banking-payment  ← depends on: banking-common, banking-account
banking-fraud    ← depends on: banking-common, banking-account, banking-payment
banking-onboarding ← depends on: banking-common, banking-account
banking-notification ← depends on: banking-common, banking-events
banking-ai-gateway  ← depends on: all domain modules (gateway only)
banking-mcp-client  ← depends on: banking-common (connects via MCP protocol, not imports)
```

**Rules:**
- Never create a circular dependency between modules
- Never import a downstream module into an upstream module (e.g., `banking-payment` must not import `banking-fraud`)
- `banking-ai-gateway` is the only module permitted to import multiple domain modules
- `banking-mcp-client` communicates with the gateway over MCP protocol — it must NOT import domain module classes directly

## banking-common Rules
- Contains: `BaseEntity`, `Money`, `ApiResponse`, exception hierarchy, common utilities
- Must NOT depend on: Spring Boot, Spring Data, Kafka, Spring AI, or any other banking module
- Exception classes must extend the typed hierarchy — adding new root-level exceptions is forbidden
- `Money` is the canonical monetary type — never introduce `BigDecimal amount` fields in domain classes

## banking-events Rules
- Contains: Kafka event records only — pure Java records with no behavior
- Must NOT depend on: Spring Boot, Spring Data, JPA, Kafka client (events module is a pure DTO module)
- Event records must be immutable (`record` keyword)
- Every event record must include `correlationId` as a field
- Event naming: `{Domain}{Action}Event` — e.g., `AccountCreatedEvent`, `PaymentCompletedEvent`

## Entity Design Rules
- All persistent entities must extend `BaseEntity` from `banking-common`
- Every entity must have `@Version Long version` for optimistic locking — non-negotiable
- Entity constructors must be `protected` (JPA requirement) + a `@Builder` constructor for code use
- Entities must not expose collection fields directly — always return unmodifiable views
- Never use `@OneToMany(cascade = CascadeType.ALL)` without confirming the cascade behavior is intentional

## Service Layer Rules
- Every service method that writes to DB must be `@Transactional`
- Read-only service methods should be `@Transactional(readOnly = true)` for performance
- Services must throw typed exceptions from `banking-common` — never raw `RuntimeException`
- Services must not catch and silently swallow exceptions — either handle meaningfully or rethrow
- Never call one service from another across domain boundaries — use domain events instead

## Value Object Rules
- `Money(BigDecimal amount, Currency currency)` — always construct with both fields
- `Money` arithmetic: use provided methods (`add`, `subtract`, `isGreaterThan`) — never unwrap to `BigDecimal` for math
- `AccountId`, `CustomerId`, `PaymentId` are `UUID` typed — never accept raw `String` in service method signatures

## What Constitutes a Domain Boundary Violation
- `banking-account` importing any class from `banking-payment` or `banking-fraud`
- `banking-payment` directly calling `banking-onboarding` service methods
- A domain service calling a method in `banking-ai-gateway`
- Using `ApplicationContext.getBean()` to bypass the declared dependency graph
