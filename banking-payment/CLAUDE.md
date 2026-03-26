# banking-payment — Module Rules

## Purpose
Payment processing: NEFT, RTGS, IMPS, UPI, SWIFT. Manages the full payment lifecycle from initiation to settlement.
Exposes 7 MCP tools via `PaymentMcpTool`.

## Payment Type Rules
| Type | Max Amount | Settlement | Reversible |
|------|-----------|------------|------------|
| IMPS | ₹2L | Immediate | No |
| NEFT | No limit | Batch (hourly) | Before batch |
| RTGS | ≥₹2L | Real-time | No |
| UPI | ₹1L | Immediate | No |
| SWIFT | No limit | 1-3 days | Before processing |

- Enforce amount limits in `PaymentService` before creating the payment record
- Payment type selection must match amount — RTGS requires minimum ₹2L; enforce this

## Idempotency (Non-Negotiable)
- Every `initiatePayment` call must check for an existing payment with the same `idempotencyKey` before creating
- `idempotencyKey` = client-provided UUID; if absent, generate one and return it in the response
- Return the existing payment if the key matches — do not create a duplicate
- Idempotency window: 24 hours

## Payment State Machine
```
PENDING → PROCESSING → COMPLETED
                     → FAILED
         → CANCELLED  (only from PENDING, before processing starts)
```
- State transitions are validated in `PaymentService` — no arbitrary status updates
- `COMPLETED` and `FAILED` are terminal states — no further transitions allowed
- On `FAILED`: release the fund hold on the source account (in the same transaction)
- On `COMPLETED`: convert hold to actual debit and credit destination account

## MCP Tools in This Module (PaymentMcpTool)
Expected tools:
1. `initiatePayment` — create a new payment (places fund hold immediately)
2. `getPaymentStatus` — query current status and details of a payment
3. `cancelPayment` — cancel a PENDING payment and release hold
4. `listPayments` — paginated payment history for an account
5. `getPaymentLimits` — retrieve current daily/monthly limits for a customer
6. `validateBeneficiary` — verify beneficiary account/IFSC before payment
7. `getPaymentReceipt` — generate a formatted receipt for a completed payment

## Fund Hold Lifecycle in This Module
- `initiatePayment` → calls `accountService.placeHold()` first → then creates `Payment` record
- The `Payment` record holds a reference to the hold ID for release
- Settlement service (async) processes the payment and calls `accountService.debitBalance()` + releases hold
- Never create a `Payment` record without a successful hold placement

## Error Handling
- `InsufficientFundsException` — when available balance < payment amount (after hold attempt fails)
- `PaymentLimitExceededException` — when daily/monthly limit is breached
- `InvalidBeneficiaryException` — when beneficiary validation fails
- `PaymentNotFoundException` — when querying a non-existent payment ID

## What NOT To Do
- Do not implement SWIFT/NEFT batch scheduling here — that belongs in an external integration service
- Do not hardcode exchange rates or fee structures — externalize to config or a dedicated rate service
- Do not allow `amount = 0` payments — validate and reject at the tool/service boundary
