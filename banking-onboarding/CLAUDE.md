# banking-onboarding — Module Rules

## Purpose
Customer lifecycle management: registration, KYC verification, AML screening, account activation.
Exposes 5 MCP tools via `OnboardingMcpTool`.

## Customer Lifecycle State Machine
```
REGISTERED → KYC_PENDING → KYC_APPROVED → ACTIVE
                         → KYC_FAILED   → REJECTED
           → AML_REVIEW  → CLEARED → KYC_PENDING (re-enter flow)
                         → BLOCKED
```
- State transitions are enforced in `CustomerOnboardingService` — no arbitrary status updates via repository
- A customer in `REJECTED` or `BLOCKED` state cannot re-register with the same identity documents
- `ACTIVE` is the only state that permits banking operations (account creation, payments)

## KYC Rules (Regulatory — Non-Negotiable)
- KYC check must complete before any account can be created for the customer
- If KYC is not approved: throw `KycNotApprovedException` (HTTP 403) — this is a gate check, not a processing failure
- If KYC processing itself fails (technical error): throw `KycFailedException` (HTTP 500) — distinct from not-approved
- Never bypass the KYC gate in service code — even for demo/test data in non-test contexts
- KYC document references (document type + reference number) are stored — actual document bytes are NOT stored here

## AML Rules
- AML screening runs asynchronously after KYC approval
- AML result is consumed via Kafka event (`banking.aml.screening.result`) — never polled synchronously
- `BLOCKED` status from AML cannot be overridden programmatically — requires manual compliance review

## PII Handling (Stricter Rules for This Module)
- This module handles the most sensitive PII in the system
- Customer name, DOB, address, document numbers must NEVER appear in log statements
- Mask all document reference numbers in logs: show first 2 + last 2 chars only
- `customerId` (UUID) is safe to log; the name associated with it is not

## MCP Tools in This Module (OnboardingMcpTool)
Actual tool names (as registered with Spring AI):
1. `get_customer_profile` — retrieve customer details (PII-safe summary only)
2. `get_customer_by_email` — look up customer by email address
3. `update_kyc_status` — update KYC verification status (approve/reject)
4. `get_pending_kyc_customers` — list customers awaiting KYC review
5. `complete_customer_onboarding` — finalize onboarding after KYC approval

## Validator Package
This module has a `validator/` package — use it:
- `CustomerRegistrationValidator` — validates registration request completeness
- `DocumentValidator` — validates document type + reference format
- Validators throw `ValidationException` (from banking-common) — not service-layer exceptions
- Call validators in the MCP tool before invoking the service

## Testing Rules Specific to This Module
- KYC state machine transitions must have exhaustive tests — every valid and invalid transition
- `KycNotApprovedException` vs `KycFailedException` must be tested as distinct cases
- Never use real document numbers or real customer names in test fixtures
- AML consumer tests require `@EmbeddedKafka`
