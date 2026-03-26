# banking-fraud — Module Rules

## Purpose
Rule-based fraud detection engine. Scores transactions and triggers holds/alerts.
Exposes 2 MCP tools via `FraudMcpTool`.

## Fraud Rule Engine Architecture (Critical Pattern)
The engine is a **pluggable scorer** — never hardcode rules inline:

```
FraudDetectionService
    └── List<FraudRule>  ← injected via Spring (all @Component rules auto-collected)
         ├── VelocityCheckRule       ← too many txns in short window
         ├── UnusualAmountRule       ← amount deviates from customer pattern
         ├── GeographicAnomalyRule   ← transaction from new location
         ├── MerchantRiskRule        ← high-risk merchant category
         ├── TimePatternRule         ← unusual transaction time
         └── DeviceFingerprintRule   ← new/unknown device
```

**Adding a new fraud rule:**
1. Create a class implementing the `FraudRule` interface: `int score(TransactionContext ctx)`
2. Annotate with `@Component` — it is auto-discovered by Spring
3. Write a unit test with at least 3 scenarios (trigger, borderline, no-trigger)
4. Document: what it detects, score range (0-100), and threshold that triggers a flag
5. Never modify existing rules to add new detection logic — add a new rule instead (Open/Closed)

## Scoring Model
- Each rule returns a score `0..100`
- Scores are summed across all rules
- Thresholds (configurable):
  - `< 30` → APPROVED (no action)
  - `30..69` → REVIEW (flag for manual review)
  - `≥ 70` → BLOCKED (auto-hold payment + alert)
- Thresholds must come from config (`banking.fraud.threshold.*`), never hardcoded

## MCP Tools in This Module (FraudMcpTool)
Expected tools:
1. `analyzeTransactionFraud` — run all rules against a transaction, return score + breakdown
2. `getFraudRulesSummary` — list active rules, their weights, and current thresholds

## Fraud Analysis Rules
- Analysis is **read-only** — `FraudMcpTool` does not modify accounts or payments
- Payment holds triggered by fraud are placed by calling `accountService.blockAccount()` in the gateway layer, not here
- `FraudDetectionService` returns a `FraudAnalysisResult` — the caller decides what to do with the score
- Never make external API calls (IP reputation, device fingerprinting services) inside a rule synchronously — use async enrichment if needed

## Test Rules Specific to This Module
- Every `FraudRule` implementation must have its own dedicated test class
- Test each rule independently with mocked `TransactionContext`
- Test the aggregated `FraudDetectionService` with a known set of rules and expected total score
- Never test fraud rules with production customer data — use generated synthetic data

## What NOT To Do
- Do not hardcode customer IDs, amounts, or thresholds in rule implementations
- Do not import `banking-ai-gateway` — fraud rules are pure domain logic
- Do not make network calls in rule scoring — scoring must be synchronous and fast (<50ms total)
- Do not add `@Transactional` to rule score methods — they are read-only computations
