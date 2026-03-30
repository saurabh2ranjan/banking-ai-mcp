---
paths:
  - "banking-events/**"
  - "banking-notification/**"
  - "banking-ai-gateway/**/consumer/**"
  - "banking-ai-gateway/**/kafka/**"
  - "banking-mcp-client/**"
  - "banking-payment/**/event/**"
  - "banking-onboarding/**/event/**"
  - "**/*Consumer.java"
  - "**/*Producer.java"
  - "**/*EventListener.java"
  - "**/application-kafka.yml"
---

# Kafka & Event Streaming Rules

## Feature Flag — Non-Negotiable
Every Kafka producer and consumer class must be gated:
```java
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")
```
The system must function fully without Kafka. Never make Kafka a hard runtime dependency.

Activation methods:
- Spring profile: `SPRING_PROFILES_ACTIVE=kafka`
- Environment variable: `BANKING_KAFKA_ENABLED=true`

## Transactional Event Publishing
- **Never** publish Kafka events inside a DB transaction — use `@TransactionalEventListener(phase = AFTER_COMMIT)`
- This prevents phantom events on rollback
- Pattern:
  ```java
  // Service publishes a Spring application event (not Kafka directly)
  eventPublisher.publishEvent(new AccountCreatedEvent(account.getId(), correlationId));

  // Separate @TransactionalEventListener picks it up AFTER_COMMIT and sends to Kafka
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountCreated(AccountCreatedEvent event) {
      kafkaTemplate.send(TOPIC_ACCOUNT_CREATED, event.accountId().toString(), event);
  }
  ```

## Correlation ID Propagation
- Every Kafka message must carry `X-Correlation-ID` as a Kafka message header
- Producer interceptor stamps the correlation ID from MDC automatically — do not set headers manually
- When the `tracing` profile is active, Micrometer Tracing auto-instruments `KafkaTemplate` and `@KafkaListener` with W3C `traceparent` headers — this coexists with the custom `X-Correlation-ID` header (they are independent)
- Every consumer must restore MDC from the Kafka header at the start of the `@KafkaListener` method:
  ```java
  @KafkaListener(topics = "banking.kyc.events")
  public void consume(ConsumerRecord<String, KycEvent> record) {
      String correlationId = extractCorrelationId(record.headers());
      MDC.put("traceId", correlationId);
      try {
          // process
      } finally {
          MDC.clear();
      }
  }
  ```

## Consumer Idempotency
- Every consumer must be idempotent — Kafka delivers at-least-once, duplicates will happen
- Check for already-processed events using a deduplication key (event ID or correlationId + topic offset)
- Never assume a consumer will receive each message exactly once

## Event Record Design
- All event records live in `banking-events` module — never define them inline in producers or consumers
- Event records must be Java `record` types (immutable)
- Required fields on every event: wrap in `EventMetadata` record which carries `eventId` (String/UUID), `timestamp` (Instant), `source` (String), `correlationId` (String)
- Event records must be serializable to JSON — no custom serializers unless unavoidable
- Topic naming convention: `banking.{domain}.{action}` — e.g., `banking.account.created`, `banking.kyc.approved`

## Error Handling in Consumers
- Use a `DeadLetterPublishingRecoverer` for failed consumer messages — never silently drop failures
- Log the full error with correlationId before sending to DLT
- Consumer retry policy: 3 attempts with exponential backoff before DLT

## Testing Kafka Code
- Unit tests: do NOT mock `KafkaTemplate` to test business logic — that hides real serialization issues
- Integration tests: use `@EmbeddedKafka` to test the full produce→consume cycle
- Always assert that the consumed message has the correct `correlationId` header in integration tests

## What NOT To Do
- Do not call `kafkaTemplate.send()` inside a `@Transactional` method body — use the AFTER_COMMIT pattern
- Do not use `String` for Kafka message keys where domain IDs exist — use the actual entity ID
- Do not share Kafka topic names as magic strings — define them as constants in `KafkaConfig` (e.g., `TOPIC_NOTIFICATIONS`, `TOPIC_PAYMENT_STATUS`)
- Do not create Kafka producers/consumers in `banking-common` or `banking-events` modules
