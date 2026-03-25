# banking-common — Module Rules

## Purpose
Foundation library for all banking modules. Contains: `BaseEntity`, `Money`, `ApiResponse`, and the full exception hierarchy.

## Hard Constraints
- **Zero Spring Boot dependencies** — this module must compile without `spring-boot-starter-*`
- Spring Framework (core/context) is acceptable; Spring Boot auto-configuration is not
- No Kafka, no Spring AI, no JPA annotations (JPA is in domain modules, not here)
- No external HTTP calls of any kind

## What Belongs Here
- `BaseEntity` — `@MappedSuperclass` with `id`, `createdAt`, `updatedAt`, `version`
- `Money(BigDecimal amount, Currency currency)` — immutable value object with arithmetic methods
- `ApiResponse<T>` — standard envelope: `success(T data)` / `error(String code, String message)`
- Exception hierarchy root classes (HTTP status is annotated here, not in individual modules)
- Shared utilities (e.g., UUID generation, date formatting)

## Exception Hierarchy Rules
- All exceptions extend `BankingException` (the root)
- Each exception carries an `errorCode` (String constant) used in `ApiResponse.error()`
- HTTP status mapping lives on the exception class via `@ResponseStatus` — not on controller advice
- Never add a new root-level exception class; extend the existing hierarchy
- Exception message must be human-readable and safe (no stack traces, no DB details)

## Adding a New Exception — Checklist
1. Identify the correct parent in the hierarchy (`NotFoundException`, `ValidationException`, `BusinessRuleException`, etc.)
2. Define a unique `ERROR_CODE` constant: `"{DOMAIN}_{REASON}"` (e.g., `"ACCOUNT_NOT_FOUND"`)
3. Add `@ResponseStatus(HttpStatus.XXX)` matching the HTTP semantics
4. Write a test in `BankingExceptionsTest` covering the new type
5. Document in this module's exception hierarchy diagram

## Money Rules
- `Money` is always immutable — arithmetic methods return new instances
- Never unwrap `Money.amount()` for arithmetic — use `money.add(other)`, `money.subtract(other)`, `money.isGreaterThan(other)`
- Currency mismatch must throw `CurrencyMismatchException` — never silently convert
