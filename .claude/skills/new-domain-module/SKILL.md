---
name: new-domain-module
description: Scaffold a new domain module (e.g., banking-loans, banking-cards) following project conventions. Use when adding a new bounded context to the monorepo.
user-invocable: true
argument-hint: "<module-name> <domain-entity>"
---

Scaffold a new domain module for this banking monorepo. The module name is `$0` and the primary domain entity is `$1`.

Follow every rule below exactly. Do not skip steps or add extras beyond what is specified.

## Step 1 — Determine the module name and package

- Module directory: `banking-$0/`
- Java base package: `com.banking.$0`
- Domain entity class: `$1` (PascalCase)

## Step 2 — Create `banking-$0/build.gradle`

Model it on an existing domain module (e.g., `banking-account/build.gradle`). Must include:
- `implementation project(':banking-common')` dependency
- MapStruct processor: `-Amapstruct.defaultComponentModel=spring`
- JaCoCo exclusions for `domain/`, `dto/`, `config/`, `exception/`, `*Application`
- Do NOT add `banking-ai-gateway`, `banking-payment`, or other domain modules unless the dependency graph explicitly allows it (check `.claude/rules/domain-invariants.md`)

## Step 3 — Add to `settings.gradle`

Add `include 'banking-$0'` to the root `settings.gradle`.

## Step 4 — Create the exact package layout

```
banking-$0/src/main/java/com/banking/$0/
├── domain/          ← JPA entities + value objects only
├── dto/             ← Java records (request/response) — immutable
├── service/         ← @Service classes with @Transactional methods
├── repository/      ← Spring Data JPA interfaces only
├── mcp/             ← @Tool-annotated methods (*McpTool class)
├── mapper/          ← MapStruct @Mapper interfaces
└── event/           ← Application event records (no persistence)
```

Do not add, rename, or merge packages.

## Step 5 — Create the domain entity

File: `domain/$1.java`

Requirements:
- Extend `BaseEntity` from `banking-common`
- Add `@Version Long version` field — mandatory for optimistic locking
- Use `@Builder` constructor for code use; keep JPA `protected` no-arg constructor
- All monetary fields must use `Money` type — never `BigDecimal`, `double`, or `float`
- Never expose collection fields directly — return `Collections.unmodifiableList(...)`
- No `@AllArgsConstructor` on entities (breaks JPA)

## Step 6 — Create request/response DTOs

Files: `dto/$1Request.java`, `dto/$1Response.java`, etc.

Requirements:
- Use Java `record` keyword — never POJOs with getters/setters
- `@Builder` on request DTOs
- `@NotNull`, `@NotBlank`, `@Positive` for validation
- Monetary fields use `Money` type with currency always present

## Step 7 — Create the MapStruct mapper

File: `mapper/$1Mapper.java`

Requirements:
- `@Mapper(componentModel = "spring")` annotation
- Component model is already set globally — but annotation is still required
- Use `@Mapping(target="field", expression="...")` for non-automappable fields
- Never write manual entity-to-DTO conversion anywhere else

## Step 8 — Create the repository

File: `repository/$1Repository.java`

Requirements:
- Extend `JpaRepository<$1, UUID>`
- Spring Data JPA interfaces only — no `@Query` unless unavoidable
- Read-only finder methods are sufficient for most cases

## Step 9 — Create the service

File: `service/$1Service.java`

Requirements:
- `@Service` + `@Slf4j`
- Constructor injection only — never `@Autowired` on fields
- Write methods: `@Transactional`
- Read methods: `@Transactional(readOnly = true)`
- Throw typed exceptions from `banking-common` — never raw `RuntimeException`
- Never catch and silently swallow exceptions
- Log with `correlationId` from MDC: `log.info("[{}] ...", MDC.get("traceId"), ...)`
- Never log PII (name, email, phone, SSN, DOB, address) — only IDs

## Step 10 — Create the MCP tool class

File: `mcp/$1McpTool.java`

Requirements:
- Class name: `$1McpTool`
- Annotate with `@Component` (not `@Service`)
- Constructor-inject the service
- Each method annotated with `@Tool(name = "...", description = "...")`
- Tool names: lowercase with underscores (e.g., `create_$0`, `get_$0_details`)
- Every parameter annotated with `@ToolParam(description = "...")` — no vague docs
- Descriptions must include: what it does, when to use it, what it returns, required params
- Catch domain exceptions and return structured `Map<String, Object>` responses — never let exceptions propagate to the AI
- Do NOT add `@Transactional` on MCP tool methods — belongs on service only

## Step 11 — Register the MCP tool in the gateway

In `banking-ai-gateway`, `BankingAiConfig.java`:
- Add the new `$1McpTool` bean to the `MethodToolCallbackProvider` builder
- Use `AopProxyUtils.getSingletonTarget(bean)` to unwrap AOP proxies — this is **required** or `@Tool` annotations are invisible to reflection and tools silently drop

## Step 12 — Write tests

Minimum coverage: 70% line + branch (JaCoCo enforced — build fails below this).

- Service tests: `@ExtendWith(MockitoExtension.class)`, mock repository
- MCP tool tests: `@ExtendWith(MockitoExtension.class)`, mock service
- Repository tests: `@DataJpaTest`
- Test method naming: `methodName_whenCondition_thenExpectedOutcome`

## Step 13 — Verify module boundaries

Before finishing, confirm:
- No circular dependencies
- `banking-$0` does not import downstream modules (fraud, payment, gateway)
- `banking-ai-gateway/build.gradle` includes `implementation project(':banking-$0')`
- Run `./gradlew :banking-$0:build` — must pass clean

## Checklist before done

- [ ] `settings.gradle` updated
- [ ] `build.gradle` for new module created
- [ ] All 7 packages created with correct classes
- [ ] `@Version Long version` on entity
- [ ] `Money` used for all monetary fields
- [ ] MapStruct mapper exists
- [ ] `$1McpTool` registered in `BankingAiConfig` with AopProxyUtils unwrap
- [ ] Tests written (70% coverage minimum)
- [ ] `./gradlew :banking-$0:build` passes
