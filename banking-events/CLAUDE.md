# banking-events — Module Rules

## Purpose
Shared Kafka event DTO module. All event records that cross module boundaries are defined here.

## Hard Constraints
- **Pure Java records only** — no Spring, no JPA, no Kafka client, no Jackson annotations unless strictly necessary for serialization
- No business logic of any kind — these are data transfer objects
- No mutable state — all fields are `final` (enforced by `record`)
- This module is a compile-time dependency for producers AND consumers — keep it minimal

## Required Fields on Every Event Record
All events include an `EventMetadata` record that carries tracing and deduplication fields:
```java
public record EventMetadata(
    String eventId,        // unique event identifier for deduplication (UUID string)
    Instant timestamp,     // when the event happened (server time)
    String source,         // originating service/module
    String correlationId   // for distributed tracing across async boundaries
) {}

public record AccountCreatedEvent(
    String accountId,
    String customerId,
    // ... domain-specific fields
    EventMetadata metadata
) {}
```

## Naming Convention
- Class name: `{Domain}{Action}Event` → `AccountCreatedEvent`, `PaymentCompletedEvent`, `KycApprovedEvent`
- Package: `com.banking.events.{domain}` → `com.banking.events.account`, `com.banking.events.kyc`
- Topic constant: defined in `KafkaConfig` in `banking-ai-gateway` (e.g., `TOPIC_NOTIFICATIONS = "banking.notifications"`)

## Topic Naming
Topic name constants are defined in `KafkaConfig` (in `banking-ai-gateway`) as static fields:
```java
public static final String TOPIC_NOTIFICATIONS   = "banking.notifications";
public static final String TOPIC_PAYMENT_STATUS  = "banking.payments.status";
public static final String TOPIC_AUDIT_TRAIL     = "banking.audit.trail";
public static final String TOPIC_KYC_STATUS      = "banking.onboarding.kyc-status";
```
Publishers reference these constants — avoid hardcoding topic names as magic strings.

## What NOT To Add
- No `@Service`, `@Component`, or any Spring annotation
- No repository or database access
- No Kafka producer/consumer code — this module does not send or receive messages
- No validation annotations (`@NotNull` etc.) — event records trust their producers
