---
paths:
  - "**/src/test/**"
  - "**/*Test.java"
  - "**/*Tests.java"
  - "**/*IT.java"
---

# Testing Rules

## Test Type Decision Matrix
| What you're testing | Test type | Annotations |
|---|---|---|
| Service business logic | Unit | `@ExtendWith(MockitoExtension.class)` |
| Repository queries | Integration | `@DataJpaTest` + H2 |
| MCP tool methods | Unit | Mock the service layer |
| REST controller | Slice | `@WebMvcTest` |
| Kafka consumer/producer | Integration | `@EmbeddedKafka` |
| Full flow | Integration | `@SpringBootTest(webEnvironment=RANDOM_PORT)` |

## Naming Convention
All test methods follow: `methodName_whenCondition_thenExpectedOutcome`

```java
// Correct
void initiatePayment_whenInsufficientFunds_thenThrowsInsufficientFundsException()
void getAccountBalance_whenAccountNotFound_thenThrowsAccountNotFoundException()
void processKyc_whenApproved_thenAccountActivated()

// Wrong
void testPayment()
void shouldThrowException()
```

## Unit Test Rules
- Always use `@ExtendWith(MockitoExtension.class)` — never `@RunWith(MockitoJUnitRunner.class)`
- Use strict mocks: Mockito will fail tests on unused stubs
- Inject mocks via constructor in `@BeforeEach`, not `@InjectMocks` (brittle with constructor injection)
- Never mock: `Money`, records, enums, or final JDK classes
- Assert both happy path AND all declared exception paths for every service method
- One logical assertion per test — use `assertSoftly` for multi-field checks

## Integration Test Rules
- Use H2 in-memory for all DB integration tests — never point at a real DB in CI
- Reset DB state between tests: use `@Transactional` on test class (rolls back) or `@Sql(scripts="/cleanup.sql")`
- Never mock the repository layer in a `@DataJpaTest` — that defeats the purpose
- For Kafka: use `@EmbeddedKafka`, never mock `KafkaTemplate` in consumer tests

## MCP Tool Test Rules
- Every `@Tool`-annotated method must have a dedicated test class `{Domain}McpToolTest`
- Test happy path + each exception the tool can throw
- Verify the tool returns a structured response (not a raw exception propagation)
- Do NOT test Spring AI / ChatClient behavior in tool tests — that's the gateway's responsibility

## Test Data Rules
- No magic strings or hardcoded amounts in test bodies — use named constants or builders
- Use `TestDataBuilder` inner static classes for complex entities; keep builders in a `testdata` package
- Never use production data or real customer IDs in test fixtures

## Coverage Rules
- JaCoCo enforces **70% minimum** line + branch coverage — `./gradlew build` fails below this
- Excluded from coverage measurement: `domain/`, `dto/`, `config/`, `*Application`, `exception/`, `events/`
- Do not write coverage-padding tests (trivial getter tests) — cover meaningful branches only
- Run coverage locally before pushing: `./gradlew test jacocoRootReport`

## What NOT To Do
- Do not use `@SpringBootTest` for service unit tests — it's slow and unnecessary
- Do not use `Mockito.mock()` inline; declare mocks as `@Mock` fields for readability
- Do not assert on `toString()` output — assert on actual field values
- Do not catch exceptions in tests to assert them; use `assertThrows()`
- Do not suppress `@SuppressWarnings("unchecked")` in tests to force compilation
