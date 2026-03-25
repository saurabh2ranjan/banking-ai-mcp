# Code Style Rules

## Package Structure
Every domain module must follow this exact layout — do not add, rename, or merge packages:
```
domain/      ← JPA entities + value objects
dto/         ← request/response records (immutable)
service/     ← business logic (@Service, @Transactional)
repository/  ← Spring Data JPA interfaces only
mcp/         ← @Tool methods (*McpTool class)
mapper/      ← MapStruct @Mapper interfaces
event/       ← application event records (no persistence)
```

## Naming Conventions
| Artifact | Pattern | Example |
|---|---|---|
| MCP tool class | `{Domain}McpTool` | `AccountMcpTool` |
| Service | `{Domain}Service` | `AccountService` |
| Repository | `{Domain}Repository` | `AccountRepository` |
| MapStruct mapper | `{Domain}Mapper` | `AccountMapper` |
| Request DTO | `{Action}{Domain}Request` | `CreateAccountRequest` |
| Response DTO | `{Domain}Response` | `AccountResponse` |
| Kafka event | `{Domain}{Action}Event` | `AccountCreatedEvent` |
| Exception | `{Reason}Exception` | `InsufficientFundsException` |

## Java Language Rules
- Use Java records for all DTOs and event payloads — never POJOs with getters/setters for immutable data
- Use sealed interfaces for domain state enums that require exhaustive matching (e.g., `PaymentStatus`)
- Prefer `var` only when the type is obvious from the right-hand side
- Constructor injection only — never `@Autowired` on fields or setters
- `@Transactional` belongs on service methods only — never on controllers or MCP tools
- No wildcard imports (`import com.banking.*` is forbidden)

## Lombok Rules
- `@Builder` — on request DTOs and domain entities
- `@Value` — on immutable value objects
- `@Data` — only on mutable DTOs that genuinely need equals/hashCode
- `@Slf4j` — on every class that logs; never use `LoggerFactory.getLogger()` manually
- Never use `@AllArgsConstructor` on entities (breaks JPA)

## MapStruct Rules
- Every entity↔DTO conversion MUST go through a `@Mapper` interface — no manual mapping code
- Mapper component model is always `spring` (set globally in build.gradle via `-Amapstruct.defaultComponentModel=spring`)
- If a field cannot be auto-mapped, use `@Mapping(target="field", expression="...")` — not a manual loop
- Mappers are `@Mapper(componentModel = "spring")` and injected as Spring beans

## Money & Financial Amounts
- All monetary amounts use the `Money` value object from `banking-common` — never `double`, `float`, or raw `BigDecimal`
- Currency must always travel with the amount — never a standalone `BigDecimal amount` field on a DTO

## What NOT To Do
- Do not add `@RestController` to any module except `banking-ai-gateway`
- Do not write `try/catch (Exception e)` generic handlers — use the typed exception hierarchy
- Do not write `System.out.println` — use `@Slf4j` and `log.info/warn/error`
- Do not use `Optional.get()` without `isPresent()` check — prefer `orElseThrow(() -> new NotFoundException(...))`
- Do not create utility classes with only static methods — prefer Spring beans
