package com.banking.fraud.mcp;

import com.banking.common.exception.BankingExceptions.PaymentNotFoundException;
import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.domain.FraudAnalysis.FraudDecision;
import com.banking.fraud.rules.FraudRules.FraudRuleResult;
import com.banking.fraud.service.FraudDetectionService;
import com.banking.payment.domain.Payment.FraudRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudMcpToolTest {

    @Mock        FraudDetectionService fraudDetectionService;
    @InjectMocks FraudMcpTool          fraudMcpTool;

    // ── helpers ───────────────────────────────────────────────────────────────

    // FraudAnalysis record field order:
    // paymentId, fraudScore, riskLevel, ruleResults, triggeredRules, decision, reasoning, analysedAt
    private FraudAnalysis buildAnalysis(String paymentId,
                                        double score,
                                        FraudRiskLevel riskLevel,
                                        List<FraudRuleResult> ruleResults,
                                        List<String> triggeredRules,
                                        FraudDecision decision) {
        return new FraudAnalysis(
                paymentId, score, riskLevel,
                ruleResults, triggeredRules,
                decision, "Automated fraud analysis reasoning",
                LocalDateTime.now()
        );
    }

    private FraudRuleResult triggeredRule(String name, double contribution, String desc) {
        return new FraudRuleResult(name, true, contribution, desc);
    }

    private FraudRuleResult passedRule(String name) {
        return new FraudRuleResult(name, false, 0.0, "Rule not triggered");
    }

    // ── analyse_payment_fraud_risk ────────────────────────────────────────────

    @Test
    void analysePaymentFraudRisk_lowRisk_returnsApproveDecision() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-001", 0.15, FraudRiskLevel.LOW,
                List.of(passedRule("LARGE_AMOUNT"), passedRule("VELOCITY_CHECK")),
                List.of(), FraudDecision.APPROVE
        );
        when(fraudDetectionService.analysePayment("PAY-001")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("PAY-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("paymentId")).isEqualTo("PAY-001");
        assertThat(result.get("fraudScore")).isEqualTo("0.150");
        assertThat(result.get("riskLevel")).isEqualTo("LOW");
        assertThat(result.get("decision")).isEqualTo("APPROVE");
        assertThat(result.get("isHighRisk")).isEqualTo(false);
        assertThat((List<?>) result.get("ruleBreakdown")).isEmpty();
        assertThat((List<?>) result.get("triggeredRules")).isEmpty();
    }

    @Test
    void analysePaymentFraudRisk_mediumRisk_returnsHoldDecision() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-002", 0.55, FraudRiskLevel.HIGH,
                List.of(
                        triggeredRule("LARGE_AMOUNT",   0.30, "Amount exceeds threshold"),
                        triggeredRule("VELOCITY_CHECK", 0.25, "Too many transactions today")
                ),
                List.of("LARGE_AMOUNT", "VELOCITY_CHECK"),
                FraudDecision.HOLD_FOR_REVIEW
        );
        when(fraudDetectionService.analysePayment("PAY-002")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("PAY-002");

        assertThat(result.get("decision")).isEqualTo("HOLD_FOR_REVIEW");
        assertThat(result.get("fraudScore")).isEqualTo("0.550");
        assertThat(result.get("isHighRisk")).isEqualTo(true);
        List<?> breakdown = (List<?>) result.get("ruleBreakdown");
        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(0).toString()).contains("LARGE_AMOUNT").contains("+0.30");
        assertThat(breakdown.get(1).toString()).contains("VELOCITY_CHECK").contains("+0.25");
    }

    @Test
    void analysePaymentFraudRisk_criticalRisk_returnsBlockDecision() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-003", 0.85, FraudRiskLevel.CRITICAL,
                List.of(
                        triggeredRule("LARGE_AMOUNT",    0.35, "Extremely large transfer"),
                        triggeredRule("UNUSUAL_COUNTRY", 0.30, "High-risk destination country"),
                        triggeredRule("VELOCITY_CHECK",  0.20, "Velocity limit exceeded")
                ),
                List.of("LARGE_AMOUNT", "UNUSUAL_COUNTRY", "VELOCITY_CHECK"),
                FraudDecision.BLOCK
        );
        when(fraudDetectionService.analysePayment("PAY-003")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("PAY-003");

        assertThat(result.get("decision")).isEqualTo("BLOCK");
        assertThat(result.get("riskLevel")).isEqualTo("CRITICAL");
        assertThat(result.get("isHighRisk")).isEqualTo(true);
        assertThat(result.get("fraudScore")).isEqualTo("0.850");
        assertThat((List<?>) result.get("ruleBreakdown")).hasSize(3);
        assertThat(result.get("triggeredRules").toString())
                .contains("LARGE_AMOUNT", "UNUSUAL_COUNTRY", "VELOCITY_CHECK");
    }

    @Test
    void analysePaymentFraudRisk_onlyTriggeredRulesAppearInBreakdown() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-004", 0.30, FraudRiskLevel.MEDIUM,
                List.of(
                        triggeredRule("LARGE_AMOUNT", 0.30, "Amount exceeds threshold"),
                        passedRule("VELOCITY_CHECK"),   // ← not triggered, excluded from breakdown
                        passedRule("UNUSUAL_COUNTRY")   // ← not triggered, excluded from breakdown
                ),
                List.of("LARGE_AMOUNT"),
                FraudDecision.APPROVE
        );
        when(fraudDetectionService.analysePayment("PAY-004")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("PAY-004");

        List<?> breakdown = (List<?>) result.get("ruleBreakdown");
        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).toString()).contains("LARGE_AMOUNT");
    }

    @Test
    void analysePaymentFraudRisk_paymentNotFound_returnsErrorWithCode() {
        when(fraudDetectionService.analysePayment("GHOST"))
                .thenThrow(new PaymentNotFoundException("GHOST"));

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("GHOST");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void analysePaymentFraudRisk_resultContainsAllRequiredFields() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-005", 0.20, FraudRiskLevel.LOW,
                List.of(), List.of(), FraudDecision.APPROVE
        );
        when(fraudDetectionService.analysePayment("PAY-005")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.analysePaymentFraudRisk("PAY-005");

        assertThat(result).containsKeys(
                "paymentId", "fraudScore", "riskLevel", "decision",
                "isHighRisk", "triggeredRules", "reasoning", "analysedAt", "ruleBreakdown"
        );
    }

    // ── get_fraud_decision_guidance ───────────────────────────────────────────

    @Test
    void getFraudDecisionGuidance_approveDecision_returnsGreenGuidance() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-001", 0.15, FraudRiskLevel.LOW,
                List.of(), List.of(), FraudDecision.APPROVE
        );
        when(fraudDetectionService.analysePayment("PAY-001")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.getFraudDecisionGuidance("PAY-001");

        assertThat(result.get("decision")).isEqualTo("APPROVE");
        assertThat(result.get("fraudScore")).isEqualTo("15.0%");
        assertThat(result.get("riskLevel")).isEqualTo("LOW");
        assertThat(result.get("guidance").toString()).contains("safe to process");
        assertThat(result.get("guidance").toString()).contains("✅");
    }

    @Test
    void getFraudDecisionGuidance_holdDecision_returnsWarningGuidance() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-002", 0.55, FraudRiskLevel.HIGH,
                List.of(), List.of("LARGE_AMOUNT"),
                FraudDecision.HOLD_FOR_REVIEW
        );
        when(fraudDetectionService.analysePayment("PAY-002")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.getFraudDecisionGuidance("PAY-002");

        assertThat(result.get("decision")).isEqualTo("HOLD_FOR_REVIEW");
        assertThat(result.get("fraudScore")).isEqualTo("55.0%");
        assertThat(result.get("guidance").toString()).contains("FRAUD_HOLD");
        assertThat(result.get("guidance").toString()).contains("4 hours");
        assertThat(result.get("guidance").toString()).contains("⚠️");
    }

    @Test
    void getFraudDecisionGuidance_blockDecision_returnsUrgentGuidance() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-003", 0.85, FraudRiskLevel.CRITICAL,
                List.of(), List.of("LARGE_AMOUNT", "UNUSUAL_COUNTRY"),
                FraudDecision.BLOCK
        );
        when(fraudDetectionService.analysePayment("PAY-003")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.getFraudDecisionGuidance("PAY-003");

        assertThat(result.get("decision")).isEqualTo("BLOCK");
        assertThat(result.get("fraudScore")).isEqualTo("85.0%");
        assertThat(result.get("riskLevel")).isEqualTo("CRITICAL");
        assertThat(result.get("guidance").toString()).contains("BLOCK immediately");
        assertThat(result.get("guidance").toString()).contains("Fraud Investigation");
        assertThat(result.get("guidance").toString()).contains("🚨");
    }

    @Test
    void getFraudDecisionGuidance_returnsRiskFactors() {
        List<String> triggeredRules = List.of("LARGE_AMOUNT", "VELOCITY_CHECK");
        FraudAnalysis analysis = buildAnalysis(
                "PAY-004", 0.55, FraudRiskLevel.HIGH,
                List.of(), triggeredRules,
                FraudDecision.HOLD_FOR_REVIEW
        );
        when(fraudDetectionService.analysePayment("PAY-004")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.getFraudDecisionGuidance("PAY-004");

        assertThat(result).containsKeys(
                "paymentId", "decision", "fraudScore", "riskLevel", "guidance", "riskFactors");
        assertThat(result.get("riskFactors").toString())
                .contains("LARGE_AMOUNT", "VELOCITY_CHECK");
    }

    @Test
    void getFraudDecisionGuidance_fraudScoreFormattedAsPercentage() {
        FraudAnalysis analysis = buildAnalysis(
                "PAY-005", 0.333, FraudRiskLevel.MEDIUM,
                List.of(), List.of(), FraudDecision.APPROVE
        );
        when(fraudDetectionService.analysePayment("PAY-005")).thenReturn(analysis);

        Map<String, Object> result = fraudMcpTool.getFraudDecisionGuidance("PAY-005");

        assertThat(result.get("fraudScore")).isEqualTo("33.3%");
    }

    // ── FraudAnalysis static helpers ──────────────────────────────────────────

    @Test
    void fraudAnalysis_decide_returnsCorrectDecisionByScore() {
        assertThat(FraudAnalysis.decide(0.20)).isEqualTo(FraudDecision.APPROVE);
        assertThat(FraudAnalysis.decide(0.39)).isEqualTo(FraudDecision.APPROVE);
        assertThat(FraudAnalysis.decide(0.40)).isEqualTo(FraudDecision.HOLD_FOR_REVIEW);
        assertThat(FraudAnalysis.decide(0.69)).isEqualTo(FraudDecision.HOLD_FOR_REVIEW);
        assertThat(FraudAnalysis.decide(0.70)).isEqualTo(FraudDecision.BLOCK);
        assertThat(FraudAnalysis.decide(0.99)).isEqualTo(FraudDecision.BLOCK);
    }

    @Test
    void fraudAnalysis_classify_returnsCorrectRiskLevelByScore() {
        assertThat(FraudAnalysis.classify(0.10)).isEqualTo(FraudRiskLevel.LOW);
        assertThat(FraudAnalysis.classify(0.24)).isEqualTo(FraudRiskLevel.LOW);
        assertThat(FraudAnalysis.classify(0.25)).isEqualTo(FraudRiskLevel.MEDIUM);
        assertThat(FraudAnalysis.classify(0.49)).isEqualTo(FraudRiskLevel.MEDIUM);
        assertThat(FraudAnalysis.classify(0.50)).isEqualTo(FraudRiskLevel.HIGH);
        assertThat(FraudAnalysis.classify(0.74)).isEqualTo(FraudRiskLevel.HIGH);
        assertThat(FraudAnalysis.classify(0.75)).isEqualTo(FraudRiskLevel.CRITICAL);
        assertThat(FraudAnalysis.classify(1.00)).isEqualTo(FraudRiskLevel.CRITICAL);
    }

    @Test
    void fraudAnalysis_isHighRisk_trueOnlyForHighAndCritical() {
        FraudAnalysis low      = buildAnalysis("P", 0.1, FraudRiskLevel.LOW,      List.of(), List.of(), FraudDecision.APPROVE);
        FraudAnalysis medium   = buildAnalysis("P", 0.3, FraudRiskLevel.MEDIUM,   List.of(), List.of(), FraudDecision.APPROVE);
        FraudAnalysis high     = buildAnalysis("P", 0.6, FraudRiskLevel.HIGH,     List.of(), List.of(), FraudDecision.HOLD_FOR_REVIEW);
        FraudAnalysis critical = buildAnalysis("P", 0.9, FraudRiskLevel.CRITICAL, List.of(), List.of(), FraudDecision.BLOCK);

        assertThat(low.isHighRisk()).isFalse();
        assertThat(medium.isHighRisk()).isFalse();
        assertThat(high.isHighRisk()).isTrue();
        assertThat(critical.isHighRisk()).isTrue();
    }
}