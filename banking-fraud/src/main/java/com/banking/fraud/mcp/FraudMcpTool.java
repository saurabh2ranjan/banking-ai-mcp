package com.banking.fraud.mcp;

import com.banking.common.exception.BankingExceptions.BankingException;
import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.rules.FraudRules;
import com.banking.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudMcpTool {

    private final FraudDetectionService fraudDetectionService;

    @Tool(name = "analyse_payment_fraud_risk",
          description = "Run all fraud detection rules against a payment. Returns fraud score (0.0-1.0), " +
                        "risk level (LOW/MEDIUM/HIGH/CRITICAL), triggered rules with explanations, " +
                        "and a decision: APPROVE, HOLD_FOR_REVIEW, or BLOCK. " +
                        "ALWAYS call this before processing payments over $10,000 or any SWIFT transfers.")
    public Map<String, Object> analysePaymentFraudRisk(
            @ToolParam(description = "Payment ID to analyse") String paymentId) {
        try {
            FraudAnalysis analysis = fraudDetectionService.analysePayment(paymentId);
            return Map.of(
                "paymentId",      analysis.paymentId(),
                "fraudScore",     String.format("%.3f", analysis.fraudScore()),
                "riskLevel",      analysis.riskLevel().name(),
                "decision",       analysis.decision().name(),
                "isHighRisk",     analysis.isHighRisk(),
                "triggeredRules", analysis.triggeredRules(),
                "reasoning",      analysis.reasoning(),
                "analysedAt",     analysis.analysedAt().toString(),
                "ruleBreakdown",  analysis.ruleResults().stream()
                    .filter(FraudRules.FraudRuleResult::triggered)
                    .map(r -> r.ruleName() + " (+" + String.format("%.2f", r.scoreContribution()) + "): " + r.description())
                    .collect(Collectors.toList())
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(name = "get_fraud_decision_guidance",
          description = "Get a plain-English explanation of the fraud decision and the recommended action " +
                        "for a bank agent or compliance officer to follow.")
    public Map<String, Object> getFraudDecisionGuidance(
            @ToolParam(description = "Payment ID to get guidance for") String paymentId) {
        FraudAnalysis analysis = fraudDetectionService.analysePayment(paymentId);

        String agentGuidance = switch (analysis.decision()) {
            case APPROVE -> "✅ Payment is safe to process. No action required.";
            case HOLD_FOR_REVIEW -> "⚠️ Place payment on FRAUD_HOLD. Assign to a fraud analyst for manual review within 4 hours. " +
                                    "Do NOT process or release funds until review is complete.";
            case BLOCK -> "🚨 BLOCK immediately. Do NOT process. Notify the customer via SMS. " +
                          "Escalate to Fraud Investigation team (Priority: HIGH). " +
                          "Consider blocking the account if pattern continues.";
        };

        return Map.of(
            "paymentId",    paymentId,
            "decision",     analysis.decision().name(),
            "fraudScore",   String.format("%.1f%%", analysis.fraudScore() * 100),
            "riskLevel",    analysis.riskLevel().name(),
            "guidance",     agentGuidance,
            "riskFactors",  analysis.triggeredRules()
        );
    }
}
