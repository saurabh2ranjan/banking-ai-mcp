package com.banking.fraud.service;

import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.domain.FraudAnalysis.FraudDecision;
import com.banking.fraud.rules.FraudRules.FraudRule;
import com.banking.fraud.rules.FraudRules.FraudRuleResult;
import com.banking.payment.domain.Payment;
import com.banking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fraud detection engine.
 *
 * Iterates all registered {@link FraudRule} implementations via Spring DI.
 * Rules are applied independently — total score is clamped to [0,1].
 *
 * In production, augment with:
 * - ML model inference (e.g. via ONNX or a Python micro-service)
 * - External bureau calls (SEON, Featurespace, Pindrop)
 * - Device fingerprint + IP reputation
 * - Graph database link analysis (e.g. detecting money mule networks)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final List<FraudRule>  rules;       // All rules injected by Spring
    private final PaymentService   paymentService;

    public FraudAnalysis analysePayment(String paymentId) {
        Payment payment = paymentService.findPayment(paymentId);
        return runRules(payment);
    }

    public FraudAnalysis analysePaymentDirect(Payment payment) {
        return runRules(payment);
    }

    private FraudAnalysis runRules(Payment payment) {
        log.info("Running {} fraud rules on payment: {}", rules.size(), payment.getPaymentId());

        List<FraudRuleResult> results = rules.stream()
            .map(rule -> rule.evaluate(payment, paymentService))
            .toList();

        double rawScore = results.stream()
            .mapToDouble(FraudRuleResult::scoreContribution)
            .sum();
        double score = Math.min(rawScore, 1.0);

        List<String> triggered = results.stream()
            .filter(FraudRuleResult::triggered)
            .map(r -> r.ruleName() + ": " + r.description())
            .collect(Collectors.toList());

        Payment.FraudRiskLevel level = FraudAnalysis.classify(score);
        FraudDecision decision       = FraudAnalysis.decide(score);

        String reasoning = buildReasoning(score, decision, triggered);

        FraudAnalysis analysis = new FraudAnalysis(
            payment.getPaymentId(), score, level,
            results, triggered, decision, reasoning,
            LocalDateTime.now()
        );

        log.info("Fraud analysis complete: paymentId={}, score={}, level={}, decision={}",
                 payment.getPaymentId(), String.format("%.3f", score), level, decision);

        return analysis;
    }

    private String buildReasoning(double score, FraudDecision decision, List<String> triggered) {
        String recommendation = switch (decision) {
            case APPROVE         -> "Transaction appears safe to process.";
            case HOLD_FOR_REVIEW -> "Transaction shows elevated risk indicators. Place on hold for manual review before releasing.";
            case BLOCK           -> "CRITICAL: Transaction has multiple high-risk signals. Block immediately and notify compliance.";
        };

        if (triggered.isEmpty()) return recommendation + " No risk factors detected.";
        return recommendation + " Risk factors: " + String.join(" | ", triggered);
    }
}
