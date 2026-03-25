# banking-events — Module Rules

## Purpose
Shared Kafka event DTO module. All event records that cross module boundaries are defined here.

## Hard Constraints
- **Pure Java records only** — no Spring, no JPA, no Kafka client, no Jackson annotations unless strictly necessary for serialization
- No business logic of any kind — these are data transfer objects
- No mutable state — all fields are `final` (enforced by `record`)
- This module is a compile-time dependency for producers AND consumers — keep it minimal

## Required Fields on Every Event Record
```java
public record AccountCreatedEvent(
    UUID eventId,          // unique event identifier for deduplication
    Instant occurredAt,    // when the event happened (server time)
    String correlationId,  // for distributed tracing across async boundaries
    // ... domain-specific fields
) {}
```

## Naming Convention
- Class name: `{Domain}{Action}Event` → `AccountCreatedEvent`, `PaymentCompletedEvent`, `KycApprovedEvent`
- Package: `com.banking.events.{domain}` → `com.banking.events.account`, `com.banking.events.kyc`
- Topic constant (defined here as a companion interface): `KafkaTopics.ACCOUNT_CREATED = "banking.account.created"`

## Topic Naming
Define all topic name constants in a single `KafkaTopics` interface in this module:
```java
public interface KafkaTopics {
    String ACCOUNT_CREATED   = "banking.account.created";
    String PAYMENT_COMPLETED = "banking.payment.completed";
    String KYC_APPROVED      = "banking.kyc.approved";
    // ...
}
```
Producers and consumers import topic names from here — never hardcode them.

## What NOT To Add
- No `@Service`, `@Component`, or any Spring annotation
- No repository or database access
- No Kafka producer/consumer code — this module does not send or receive messages
- No validation annotations (`@NotNull` etc.) — event records trust their producers
