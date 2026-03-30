# banking-fraud — Module Rules

## Purpose
Rule-based fraud detection engine. Scores payments and triggers holds/alerts.
Exposes 2 MCP tools via `FraudMcpTool`.

## Fraud Rule Engine Architecture (Critical Pattern)
The engine is a **pluggable scorer** — never hardcode rules inline:

```
FraudDetectionService
    └── List<FraudRule>  ← injected via Spring (all @Component rules auto-collected)
         ├── HighValueRule          ← large amount (tiered: >50K, >200K, >1M)
         ├── VelocityRule           ← too many payments in 1h window
         ├── OffHoursRule           ← transaction outside business hours (22:00–06:00)
         ├── InternationalWireRule  ← SWIFT payment type
         ├── RoundAmountRule        ← suspiciously round amount
         └── DailyLimitRule         ← projected daily total exceeds 200K threshold
```

All rules are **nested `@Component` static classes inside `FraudRules.java`** — not separate files.

**Adding a new fraud rule:**
1. Add a nested `@Component` static class inside `FraudRules.java` implementing `FraudRule`
2. Signature: `FraudRuleResult evaluate(Payment payment, PaymentService paymentService)`
3. Return `FraudRuleResult(ruleName, triggered, scoreContribution, description)` — never throw
4. Write a unit test with at least 2 scenarios (trigger, no-trigger)
5. Never modify existing rules to add new detection logic — add a new rule instead (Open/Closed)

## Scoring Model
- Each rule returns a `double` score contribution `0.0..~0.45`
- Scores are summed across all triggered rules, capped at 1.0
- Thresholds (in `FraudAnalysis.decide()`):
  - `< 0.40` → APPROVE (no action)
  - `0.40..0.69` → HOLD_FOR_REVIEW (flag for manual review)
  - `>= 0.70` → BLOCK (auto-hold payment + alert)

## MCP Tools in This Module (FraudMcpTool)
Actual tool names (as registered with Spring AI):
1. `analyse_payment_fraud_risk` — run all rules against a payment by ID, return score + triggered rules + decision
2. `get_fraud_decision_guidance` — plain-English explanation of the fraud decision and recommended action

## Fraud Analysis Rules
- Analysis is **read-only** — `FraudMcpTool` does not modify accounts or payments
- Payment holds triggered by fraud are placed by the gateway layer, not here
- `FraudDetectionService` returns a `FraudAnalysis` — the caller decides what to do with the score
- Never make external API calls (IP reputation, device fingerprinting services) inside a rule synchronously — use async enrichment if needed

## Test Rules Specific to This Module
- Every `FraudRule` implementation must have its own dedicated test class
- Test each rule independently with mocked `Payment` and `PaymentService`
- Test the aggregated `FraudDetectionService` with a known set of rules and expected total score
- Never test fraud rules with production customer data — use generated synthetic data

## What NOT To Do
- Do not hardcode customer IDs, amounts, or thresholds in rule implementations
- Do not import `banking-ai-gateway` — fraud rules are pure domain logic
- Do not make network calls in rule scoring — scoring must be synchronous and fast (<50ms total)
- Do not add `@Transactional` to rule score methods — they are read-only computations
- Do not create separate files for rules — add nested static classes inside `FraudRules.java`
