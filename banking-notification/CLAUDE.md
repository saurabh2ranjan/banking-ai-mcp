# banking-notification — Module Rules

## Purpose
Notification delivery: email, SMS, push. Consumes domain events from Kafka and dispatches notifications.
This module is a pure consumer — it never initiates domain operations.

## Core Design Principle
**Notification is fire-and-forget** — notification failures must never fail a business transaction.
- Notification logic runs in a separate transaction context from domain operations
- If a notification fails (delivery error, template error), log the failure and continue — do not rethrow
- Notification delivery is idempotent — duplicate event delivery must not send duplicate notifications

## PII Rules (Stricter Here)
- Notifications by definition contain PII (customer name, email, phone)
- Log only: `customerId`, `notificationType`, `deliveryStatus` — never log the actual message content
- Never log email addresses or phone numbers, even partially masked
- Notification templates are stored in config/resources — never constructed by concatenating user-provided strings

## Kafka Consumer Rules
- Consumes events from `banking-events` module event types (AccountCreatedEvent, PaymentCompletedEvent, etc.)
- Every consumer is gated by `@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true")`
- Consumer must restore `MDC["traceId"]` from the Kafka `X-Correlation-ID` header
- Use a `DeadLetterPublishingRecoverer` — failed notifications go to a DLT, not silently dropped
- Retry policy: 3 attempts with 1s/3s/10s backoff before DLT

## NotificationService Rules
- `NotificationService` is the single entry point — never call channel-specific services (EmailService, SmsService) directly from consumers
- Channel selection (email/SMS/push) is determined by the customer's notification preferences
- Template rendering must be sandboxed — never use `String.format()` with user-controlled input (use a template engine like Thymeleaf)
- Always check if the customer has opted out before sending

## What This Module Must NOT Do
- Never call domain services (`AccountService`, `PaymentService`) — it only reads event data
- Never write to the domain database — it may have its own notification log table only
- Never expose REST endpoints for sending notifications ad-hoc (security risk)
- Never block the Kafka consumer thread on slow notification delivery — use async dispatch

## Testing Rules
- Unit test `NotificationService` with mocked channel services
- Integration test consumers with `@EmbeddedKafka` — verify the correct notification type is dispatched per event
- Test the opt-out check to ensure no notification is sent to opted-out customers
- Test DLT behavior: verify that a delivery failure after retries sends to DLT without crashing the consumer
