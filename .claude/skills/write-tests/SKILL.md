---
name: write-tests
description: Write tests for a service, MCP tool, repository, or Kafka component following project conventions. Use when adding or fixing tests to hit the 70% JaCoCo coverage minimum.
user-invocable: true
argument-hint: "<module> <ClassName>"
---

Write tests for `$1` in the `$0` module.

## Test Coverage Requirements

JaCoCo enforces **70% minimum line + branch coverage** — `./gradlew build` fails below this.

**Excluded from coverage** (do not write tests for these):
- `domain/` — JPA entities
- `dto/` — record DTOs
- `config/` — Spring configuration classes
- `exception/` — exception classes
- `*Application.java` — Spring Boot entry point

**Must be tested:**
- `service/` — all business logic paths
- `mcp/` — all tool methods + error handling
- `repository/` — custom query methods
- `filter/`, `consumer/`, `kafka/` — infrastructure components

## Step 1 — Read the class to test

Read `$1.java` fully before writing any tests. Identify:
- All public methods
- All branches (if/else, switch, try/catch)
- All exceptions thrown
- Dependencies to mock

## Test Types by Class

### Service class (`*Service.java`)

**Framework:** `@ExtendWith(MockitoExtension.class)`

```java
@ExtendWith(MockitoExtension.class)
class $1Test {

    @Mock
    private $1Repository repository;        // mock all injected dependencies

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private $1 service;

    @Test
    void methodName_whenCondition_thenExpectedOutcome() {
        // given
        when(repository.findById(any())).thenReturn(Optional.of(entity));

        // when
        var result = service.methodName(input);

        // then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    @Test
    void methodName_whenNotFound_thenThrowsNotFoundException() {
        // given
        when(repository.findById(any())).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.methodName(UUID.randomUUID()))
            .isInstanceOf(AccountNotFoundException.class);
    }
}
```

### MCP Tool class (`*McpTool.java`)

**Framework:** `@ExtendWith(MockitoExtension.class)`

Focus on: happy path returns success map, each exception type returns error map.

```java
@ExtendWith(MockitoExtension.class)
class $1Test {

    @Mock
    private $1Service service;

    @InjectMocks
    private $1 mcpTool;

    @Test
    void toolName_whenValidInput_thenReturnsSuccessStatus() {
        // given
        when(service.find(any())).thenReturn(mockEntity);

        // when
        var result = mcpTool.toolName("valid-input");

        // then
        assertThat(result.get("status")).isEqualTo("success");
    }

    @Test
    void toolName_whenEntityNotFound_thenReturnsErrorStatus() {
        // given
        when(service.find(any())).thenThrow(new AccountNotFoundException("ACC-001"));

        // when
        var result = mcpTool.toolName("ACC-001");

        // then
        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result).containsKey("message");
    }
}
```

### Repository custom methods (`*Repository.java`)

**Framework:** `@DataJpaTest`

```java
@DataJpaTest
class $1Test {

    @Autowired
    private $1 repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void customMethod_whenDataExists_thenReturnsCorrectResults() {
        // given — persist test data
        var entity = em.persistAndFlush(testEntity());

        // when
        var result = repository.customMethod(entity.getId());

        // then
        assertThat(result).isPresent();
    }
}
```

### Kafka consumer (`*Consumer.java`)

**Framework:** `@ExtendWith(MockitoExtension.class)` for unit, `@EmbeddedKafka` for integration.

```java
@ExtendWith(MockitoExtension.class)
class $1Test {

    @InjectMocks
    private $1 consumer;

    @Test
    void consume_whenValidEvent_thenProcessedSuccessfully() {
        var event = testEvent("corr-123");

        consumer.consume(event, "corr-123");

        // assert side effects
    }

    @Test
    void consume_whenDuplicateEvent_thenIdempotentlyIgnored() {
        // set up already-processed state
        consumer.consume(alreadySeenEvent, "corr-123");
        // verify no duplicate processing
    }
}
```

### Filter / interceptor (`*Filter.java`)

**Framework:** `MockMvc` or `@WebMvcTest`

```java
@WebMvcTest
class $1Test {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void filter_whenCorrelationIdProvided_thenEchoedInResponse() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .header("X-Correlation-ID", "test-id"))
            .andExpect(header().string("X-Correlation-ID", "test-id"));
    }

    @Test
    void filter_whenNoCorrelationIdProvided_thenGeneratesOne() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(header().exists("X-Correlation-ID"));
    }
}
```

## Naming Convention

**Mandatory:** `methodName_whenCondition_thenExpectedOutcome`

```
// Good
getAccountBalance_whenAccountExists_thenReturnsBalance
getAccountBalance_whenAccountNotFound_thenThrowsNotFoundException
initiatePayment_whenInsufficientFunds_thenThrowsInsufficientFundsException
analyseRisk_whenHighRiskPayment_thenReturnsBlockDecision

// Bad
testGetBalance()
shouldReturnBalance()
getBalance_success()
```

## Coverage checklist

For each method in `$1`, verify tests cover:

- [ ] Happy path (valid input, success response)
- [ ] Each exception type thrown (one test per exception)
- [ ] Each meaningful branch (`if`/`else`/`switch`)
- [ ] Null/empty inputs where relevant
- [ ] Boundary conditions (zero amounts, empty lists, max values)

## Run and verify

```bash
./gradlew :$0:test --tests "com.banking.$0_package.$1Test"
./gradlew :$0:test jacocoTestReport
# Open: $0/build/reports/jacoco/test/html/index.html
```

Build must pass with ≥70% line + branch coverage on `$1`.
