---
name: add-kafka-event
description: Add a new Kafka event to the banking-events module and wire up a publisher and consumer. Use when a domain state change needs to be streamed to other services.
user-invocable: true
argument-hint: "<Domain> <Action> e.g. 'Account Closed' or 'Payment Reversed'"
---

Add a new Kafka event for the `$0` domain, action `$1`.

Resulting event class: `$0$1Event`
Topic name convention: `banking.$0_lower.$1_lower` (e.g., `banking.account.closed`)

## Step 1 — Create the event record in `banking-events`

File: `banking-events/src/main/java/com/banking/events/$0_lower/$0$1Event.java`

```java
package com.banking.events.$0_lower;

import com.banking.events.EventMetadata;

/**
 * Published when $0 is $1.
 * Partition key: [primary entity ID field]
 */
public record $0$1Event(
    String correlationId,              // Always required — tracing
    EventMetadata metadata,            // eventId, timestamp, source
    // ... domain-specific fields ...
) {}
```

### Rules for the event record
- Must use the Java `record` keyword — immutable, no behavior
- Must include `correlationId` as a field — non-negotiable for tracing
- Must include `EventMetadata` — carries `eventId`, `timestamp`, `source`
- All monetary fields use `Money` type — never `BigDecimal`, `double`, or `float`
- Include the primary entity ID (e.g., `accountId`, `paymentId`) for partition key routing
- No Spring annotations — `banking-events` must remain a pure Java module

### What NOT to add
- No `@Entity`, `@Component`, `@Service` — this module has no Spring dependencies
- No business logic — records are pure data carriers
- No database fields — events are not persisted in `banking-events`

## Step 2 — Add the Kafka publisher in the domain module

The domain module (e.g., `banking-account`) publishes events via `@TransactionalEventListener`.

### 2a — Create a Spring application event (optional bridge)

If you want to decouple the service from Kafka directly, publish a Spring `ApplicationEvent` first, then have a listener forward it to Kafka.

File: `banking-$0_lower/src/main/java/com/banking/$0_lower/event/$0$1ApplicationEvent.java`

```java
public record $0$1ApplicationEvent(
    $0$1Event kafkaEvent
) {}
```

### 2b — Publish from the service

In the `$0Service`, after the DB write:

```java
// Publish Spring application event (Kafka forwarding happens in listener)
applicationEventPublisher.publishEvent(new $0$1ApplicationEvent(
    new $0$1Event(
        MDC.get("traceId"),
        EventMetadata.now("banking-$0_lower"),
        // ... domain fields ...
    )
));
```

### 2c — Forward to Kafka via @TransactionalEventListener

File: `banking-$0_lower/src/main/java/com/banking/$0_lower/event/$0KafkaPublisher.java`

```java
@Slf4j
@Component
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class $0KafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public $0KafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on$0$1($$0$1ApplicationEvent event) {
        var kafkaEvent = event.kafkaEvent();
        var headers = new RecordHeaders();
        headers.add("X-Correlation-ID", kafkaEvent.correlationId().getBytes());

        kafkaTemplate.send(new ProducerRecord<>(
            "banking.$0_lower.$1_lower",
            null,
            kafkaEvent.primaryEntityId(),  // partition key
            kafkaEvent,
            headers
        ));

        log.info("[{}] Published $0$1Event for id={}",
            kafkaEvent.correlationId(), kafkaEvent.primaryEntityId());
    }
}
```

**Critical:** `@TransactionalEventListener(phase = AFTER_COMMIT)` — Kafka messages are only sent after the DB transaction commits. Never use `@EventListener` for Kafka publishers.

## Step 3 — Create the consumer (in the receiving module)

If another module consumes this event (e.g., `banking-notification`, `banking-mcp-client`):

File: `src/main/java/com/banking/<consumer-module>/consumer/$0$1Consumer.java`

```java
@Slf4j
@Component
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
public class $0$1Consumer {

    @KafkaListener(topics = "banking.$0_lower.$1_lower", groupId = "${spring.kafka.consumer.group-id}")
    public void consume($0$1Event event, @Header("X-Correlation-ID") String correlationId) {
        MDC.put("traceId", correlationId);
        try {
            log.info("[{}] Received $0$1Event for id={}", correlationId, event.primaryEntityId());

            // Idempotency check — process only if not already handled
            // Business logic here...

        } finally {
            MDC.remove("traceId");
        }
    }
}
```

### Consumer rules
- Always restore `MDC["traceId"]` from the Kafka header at the start — and remove it in `finally`
- Implement idempotency — the same event may be delivered more than once
- Never throw from `@KafkaListener` unless you want the message to be retried (use DLT pattern)
- Log with correlation ID on every significant step — never blind processing

## Step 4 — Add topic configuration

In `banking-ai-gateway/src/main/resources/application.yml` (or the consuming module's yml), add topic creation config if needed:

```yaml
# Only needed if auto-create topics is disabled
spring:
  kafka:
    admin:
      auto-create: true
```

Kafka topic naming: `banking.{domain}.{action}` — all lowercase, dots as separators.

## Step 5 — Write tests

### Publisher test (integration — `@EmbeddedKafka`)

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"banking.$0_lower.$1_lower"})
class $0KafkaPublisherTest {
    @Test
    void when$0$1_thenEventPublishedToKafka() {
        // trigger the service method that publishes the event
        // consume from embedded Kafka and assert fields
        // verify correlationId propagated to header
    }
}
```

### Consumer test (unit — mock behavior)

```java
@ExtendWith(MockitoExtension.class)
class $0$1ConsumerTest {
    @Test
    void consume_whenValidEvent_thenProcessed() { ... }

    @Test
    void consume_whenDuplicateEvent_thenIdempotentlyIgnored() { ... }
}
```

## Step 6 — Verify

```bash
./gradlew :banking-events:build
./gradlew :banking-$0_lower:test
```

## Checklist

- [ ] Event record in `banking-events` — pure `record`, no Spring deps, has `correlationId` + `EventMetadata`
- [ ] Publisher uses `@TransactionalEventListener(phase = AFTER_COMMIT)` — NOT `@EventListener`
- [ ] Publisher and consumer both gated by `@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")`
- [ ] Consumer restores `MDC["traceId"]` from Kafka header, removes in `finally`
- [ ] Consumer is idempotent
- [ ] Topic name follows `banking.{domain}.{action}` convention
- [ ] `./gradlew :banking-events:build` passes — no Spring deps introduced
