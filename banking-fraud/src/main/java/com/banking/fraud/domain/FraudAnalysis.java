package com.banking.fraud.domain;

import com.banking.fraud.rules.FraudRules.FraudRuleResult;
import com.banking.payment.domain.Payment;

import java.time.LocalDateTime;
import java.util.List;

public record FraudAnalysis(
        String paymentId,
        double fraudScore,
        Payment.FraudRiskLevel riskLevel,
        List<FraudRuleResult> ruleResults,
        List<String> triggeredRules,
        FraudDecision decision,
        String reasoning,
        LocalDateTime analysedAt
) {
    public enum FraudDecision { APPROVE, HOLD_FOR_REVIEW, BLOCK }

    public boolean isHighRisk() {
        return riskLevel == Payment.FraudRiskLevel.HIGH
            || riskLevel == Payment.FraudRiskLevel.CRITICAL;
    }

    public static FraudDecision decide(double score) {
        if (score >= 0.70) return FraudDecision.BLOCK;
        if (score >= 0.40) return FraudDecision.HOLD_FOR_REVIEW;
        return FraudDecision.APPROVE;
    }

    public static Payment.FraudRiskLevel classify(double score) {
        if (score >= 0.75) return Payment.FraudRiskLevel.CRITICAL;
        if (score >= 0.50) return Payment.FraudRiskLevel.HIGH;
        if (score >= 0.25) return Payment.FraudRiskLevel.MEDIUM;
        return Payment.FraudRiskLevel.LOW;
    }
}
