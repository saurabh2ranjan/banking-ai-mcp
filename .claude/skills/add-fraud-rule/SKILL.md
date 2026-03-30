---
name: add-fraud-rule
description: Add a new fraud detection rule to the pluggable rule engine. Use when adding a new signal or heuristic to the fraud scoring system.
user-invocable: true
argument-hint: "<RuleName> <description>"
---

Add a new fraud detection rule named `$0Rule` to the banking fraud module.

The rule should detect: $1

## How the fraud engine works

The fraud engine in `banking-fraud` uses a pluggable, component-scanned scoring model:

1. `FraudDetectionService` injects `List<FraudRule>` — Spring auto-discovers all `@Component` beans implementing `FraudRules.FraudRule`
2. Each rule evaluates a `Payment` entity (with access to `PaymentService` for lookups) and returns a `FraudRuleResult(ruleName, triggered, scoreContribution, description)`
3. Score contributions are doubles (0.0–1.0), summed and capped at 1.0:
   - `< 0.40` → APPROVE
   - `0.40–0.70` → HOLD_FOR_REVIEW
   - `≥ 0.70` → BLOCK
4. `FraudMcpTool` exposes the result to AI via `analyse_payment_fraud_risk`

**Adding a new rule = adding a new nested `@Component` static class inside `FraudRules.java`** — no other wiring needed.

## Step 1 — Read the existing rules

Read `banking-fraud/src/main/java/com/banking/fraud/rules/FraudRules.java` to understand the interface and existing patterns. All rules are nested static `@Component` classes implementing the `FraudRule` interface.

## Step 2 — Add the rule class

Add a new nested static class inside `FraudRules.java`:

```java
// ─── Rule N: $0 ────────────────────────────────────────────────

@Component
public static class $0Rule implements FraudRule {

    @Override
    public FraudRuleResult evaluate(Payment payment, PaymentService paymentService) {
        // Your detection logic here.
        // Return FraudRuleResult with:
        //   - ruleName: unique identifier (e.g., "$0")
        //   - triggered: true if rule fires
        //   - scoreContribution: 0.0 if not triggered, 0.08–0.45 if triggered
        //   - description: human-readable explanation

        boolean triggered = /* your condition */;
        return new FraudRuleResult("$0", triggered,
            triggered ? 0.15 : 0.0,
            triggered ? "Suspicious pattern detected" : "No anomaly");
    }
}
```

### Score contribution guidelines
- Return `0.0` if the rule does not apply
- Return `0.08–0.15` for mild anomalies (low confidence)
- Return `0.15–0.30` for moderate signals (clear pattern match)
- Return `0.30–0.45` for high-confidence fraud signals
- **Never return negative scores** — abstain by returning 0.0
- **Never throw exceptions** from `evaluate()` — catch internal errors and return a safe result

### Available data in `evaluate()`
- `payment` — the `Payment` entity with: amount (`BigDecimal`), currency, paymentType (NEFT/RTGS/IMPS/UPI/SWIFT), sourceAccountId, destinationAccountId, status, timestamps
- `paymentService` — for lookups like `getRecentPayments(accountId, hours)` and `getDailySpendingSummary(accountId)`

### What NOT to do
- Do not make external API calls — keep rules fast and synchronous
- Do not log PII (name, email, address) — only log `paymentId`, `accountId`, score value
- Do not add `@Transactional` — rules are read-only and stateless
- Do not create a separate file — add the nested class inside `FraudRules.java`

## Step 3 — Write the test

File: `banking-fraud/src/test/java/com/banking/fraud/rules/$0RuleTest.java`

```java
@ExtendWith(MockitoExtension.class)
class $0RuleTest {

    private FraudRules.$0Rule rule;

    @Mock
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        rule = new FraudRules.$0Rule();
    }

    @Test
    void evaluate_whenRuleDoesNotApply_thenNotTriggered() {
        // given
        var payment = buildNormalPayment();

        // when
        var result = rule.evaluate(payment, paymentService);

        // then
        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreContribution()).isEqualTo(0.0);
    }

    @Test
    void evaluate_whenSuspiciousPattern_thenTriggeredWithScore() {
        // given
        var payment = buildSuspiciousPayment(); // build a payment that triggers this rule

        // when
        var result = rule.evaluate(payment, paymentService);

        // then
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isGreaterThan(0.0);
        assertThat(result.ruleName()).isEqualTo("$0");
    }
}
```

## Step 4 — Verify end-to-end

1. Run unit tests: `./gradlew :banking-fraud:test`
2. Check the rule appears in `analyse_payment_fraud_risk` output under `triggeredRules` for matching inputs
3. Verify build passes with coverage: `./gradlew :banking-fraud:build`

## Checklist

- [ ] Rule added as nested `@Component` static class in `FraudRules.java`
- [ ] Implements `FraudRule` interface with `evaluate(Payment, PaymentService)` signature
- [ ] Returns `FraudRuleResult` — never throws
- [ ] No PII in descriptions — only IDs and numeric values
- [ ] No DB access beyond `PaymentService` methods
- [ ] Test covers: rule doesn't apply (score=0.0), suspicious detection (score>0.0)
- [ ] `./gradlew :banking-fraud:build` passes
