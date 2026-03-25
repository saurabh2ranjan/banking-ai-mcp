# banking-account — Module Rules

## Purpose
Account lifecycle management: creation, balance queries, limits, fund holds, account status changes.
Exposes 6 MCP tools via `AccountMcpTool`.

## Domain Invariants
- An account balance can never go below zero (enforce in service, not DB constraint alone)
- Fund holds reduce `availableBalance` immediately — `actualBalance` changes only on settlement
- Account status transitions: `PENDING → ACTIVE → SUSPENDED → CLOSED` — no backwards transitions
- A `CLOSED` account rejects all operations except balance queries
- Currency is set at account creation and never changes

## Fund Hold Pattern (Critical)
```
initiatePayment()
  → accountService.placeHold(accountId, amount)   ← immediate, before any external call
  → paymentService.createPayment(...)
  → [async settlement]
  → accountService.releaseHold(...) OR accountService.debitBalance(...)
```
- Never skip the hold step — it prevents double-spending on concurrent payment requests
- Hold release must happen in BOTH success and failure paths — use `try/finally`
- `@Version` on `Account` entity catches concurrent hold attempts (optimistic lock exception → retry or reject)

## MCP Tools in This Module (AccountMcpTool)
Expected tools (do not rename or remove without updating BankingAiConfig):
1. `getAccountBalance` — retrieve balance + status for an account
2. `createAccount` — open a new account for an existing customer
3. `updateAccountLimits` — modify daily/monthly transaction limits
4. `blockAccount` — suspend an account (fraud/compliance trigger)
5. `getTransactionHistory` — paginated list of recent transactions
6. `getSpendingSummary` — aggregated spending by category/period

## Service Rules Specific to This Module
- `AccountService.getAccount()` must throw `AccountNotFoundException` (not return `Optional`)
- Balance operations must be `@Transactional` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` for hold/debit
- Never expose `Account` entity directly — always map through `AccountMapper` to `AccountResponse`
- Account number generation must use a collision-resistant strategy (UUID-based, not sequential)

## What NOT To Do
- Do not import `banking-payment` or `banking-fraud` — this module has no downstream dependencies
- Do not implement payment logic here — holds yes, actual payment processing no
- Do not add a `deleteAccount` operation — accounts are closed, never deleted (audit trail requirement)
