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

1. `FraudDetectionService` injects `List<FraudRule>` — Spring auto-discovers all `@Component` beans implementing `FraudRule`
2. Each rule scores a payment (0–100), higher = riskier
3. Scores are summed and thresholds applied:
   - `< 30` → APPROVE
   - `30–69` → HOLD_FOR_REVIEW
   - `≥ 70` → BLOCK
4. `FraudMcpTool` exposes the result to AI via `analyse_payment_fraud_risk`

**Adding a new rule = adding a new `@Component`** — no other wiring needed.

## Step 1 — Read the existing rules

Read the existing rule files in `banking-fraud/src/main/java/com/banking/fraud/rules/` to understand the interface and pattern. There should be a `FraudRule` interface with a `score(...)` method. Understand the input data model before writing anything.

## Step 2 — Create the rule class

File: `banking-fraud/src/main/java/com/banking/fraud/rules/$0Rule.java`

```java
package com.banking.fraud.rules;

import com.banking.fraud.domain.FraudCheckContext; // or whatever the input type is
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class $0Rule implements FraudRule {

    @Override
    public int score(FraudCheckContext context) {
        // Your detection logic here.
        // Return 0 if rule doesn't apply, higher if suspicious.
        // Max contribution: keep proportional to rule confidence.

        log.debug("[{}] $0Rule evaluated: score={}",
            MDC.get("traceId"), score);

        return score;
    }

    @Override
    public String getRuleName() {
        return "$0Rule";
    }
}
```

### Scoring guidelines
- Return `0` if the rule does not apply to this payment context
- Return `10–30` for mild anomalies (slightly unusual, low confidence)
- Return `30–60` for moderate signals (clear pattern match)
- Return `60–100` for high-confidence fraud signals
- **Never return negative scores** — abstain by returning 0
- **Never throw exceptions** from `score()` — catch internal errors and return 0 with a warning log

### What NOT to do
- Do not call external services — keep rules fast and synchronous
- Do not access the database in a rule — context data should already be loaded by `FraudDetectionService`
- Do not log PII (name, email, address) — only log `paymentId`, `accountId`, score value
- Do not add `@Transactional` — rules are read-only and stateless

## Step 3 — Understand the context model

Read `FraudCheckContext` (or whatever the input model is) to know what data is available. Common fields include:
- Payment amount, currency, type (NEFT/RTGS/IMPS/UPI/SWIFT)
- Source/destination account IDs
- Customer's payment history (velocity data)
- Timestamp, device fingerprint, geographic info

If the rule needs data not currently in the context, add it to `FraudCheckContext` and update `FraudDetectionService` to populate it.

## Step 4 — Write the test

File: `banking-fraud/src/test/java/com/banking/fraud/rules/$0RuleTest.java`

```java
@ExtendWith(MockitoExtension.class)
class $0RuleTest {

    private $0Rule rule;

    @BeforeEach
    void setUp() {
        rule = new $0Rule();
    }

    @Test
    void score_whenRuleDoesNotApply_thenReturnsZero() {
        // given
        var context = normalPaymentContext();

        // when
        int score = rule.score(context);

        // then
        assertThat(score).isEqualTo(0);
    }

    @Test
    void score_whenHighRiskPattern_thenReturnsHighScore() {
        // given
        var context = suspiciousPaymentContext(); // build a context that triggers this rule

        // when
        int score = rule.score(context);

        // then
        assertThat(score).isGreaterThan(50);
    }

    @Test
    void score_whenExceptionOccurs_thenReturnsZeroSafely() {
        // Verify the rule doesn't throw — fraud engine must never fail
    }
}
```

## Step 5 — Verify end-to-end

1. Run unit tests: `./gradlew :banking-fraud:test`
2. Check the rule appears in `analyse_payment_fraud_risk` output under `triggeredRules` for matching inputs
3. Verify build passes with coverage: `./gradlew :banking-fraud:build`

## Checklist

- [ ] Rule class created with `@Component` + `@Slf4j`
- [ ] `score()` never throws — all exceptions caught internally
- [ ] No PII in logs — only IDs and numeric scores
- [ ] No DB access in rule — reads from `FraudCheckContext` only
- [ ] Test covers: rule doesn't apply (score=0), high-risk detection (score>50), exception safety
- [ ] `./gradlew :banking-fraud:build` passes
