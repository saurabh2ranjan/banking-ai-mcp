package com.banking.fraud.rules;

import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.rules.FraudRules.*;
import com.banking.fraud.service.FraudDetectionService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.DailySpendingSummary;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRulesAndDetectionTest {

    @Mock PaymentService paymentService;

    private Payment buildPayment(BigDecimal amount, Payment.PaymentType type) {
        return Payment.builder()
                .paymentId("PAY-001")
                .sourceAccountId("ACC-SRC")
                .destinationAccountId("ACC-DST")
                .amount(amount).currency("USD")
                .paymentType(type)
                .status(Payment.PaymentStatus.PENDING_FRAUD_CHECK)
                .build();
    }

    private DailySpendingSummary dailySummary(BigDecimal total) {
        return new DailySpendingSummary("ACC-SRC", total, 3,
                BigDecimal.valueOf(50000), BigDecimal.valueOf(75000), "USD");
    }

    // ── HighValueRule ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "1000,    false, 0.00",
        "10000,   false, 0.00",
        "10001,   true,  0.15",
        "50000,   true,  0.15",
        "50001,   true,  0.30",
        "100000,  true,  0.30",
        "100001,  true,  0.45",
        "999999,  true,  0.45"
    })
    void highValueRule_thresholds(double amount, boolean triggered, double score) {
        var rule   = new HighValueRule();
        var result = rule.evaluate(buildPayment(BigDecimal.valueOf(amount), Payment.PaymentType.IMPS), paymentService);
        assertThat(result.triggered()).isEqualTo(triggered);
        assertThat(result.scoreContribution()).isEqualTo(score);
        assertThat(result.ruleName()).isEqualTo("HIGH_VALUE");
    }

    // ── VelocityRule ───────────────────────────────────────────────────────────

    @Test
    void velocityRule_zeroPayments_notTriggered() {
        when(paymentService.getRecentPayments(anyString(), eq(1))).thenReturn(List.of());
        var result = new VelocityRule().evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreContribution()).isZero();
    }

    @Test
    void velocityRule_fourPayments_notTriggered() {
        when(paymentService.getRecentPayments(anyString(), eq(1)))
                .thenReturn(Collections.nCopies(4, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
        assertThat(new VelocityRule().evaluate(
                buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService).triggered()).isFalse();
    }

    @Test
    void velocityRule_fivePayments_triggeredWithScore025() {
        when(paymentService.getRecentPayments(anyString(), eq(1)))
                .thenReturn(Collections.nCopies(5, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
        var result = new VelocityRule().evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(0.25);
    }

    @Test
    void velocityRule_tenPayments_criticalVelocityScore040() {
        when(paymentService.getRecentPayments(anyString(), eq(1)))
                .thenReturn(Collections.nCopies(10, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
        var result = new VelocityRule().evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(0.40);
    }

    @Test
    void velocityRule_fifteenPayments_stillCapsAtScore040() {
        when(paymentService.getRecentPayments(anyString(), eq(1)))
                .thenReturn(Collections.nCopies(15, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
        assertThat(new VelocityRule().evaluate(
                buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService).scoreContribution()).isEqualTo(0.40);
    }

    // ── OffHoursRule ───────────────────────────────────────────────────────────

    @Test
    void offHoursRule_resultIsCoherentRegardlessOfSystemClock() {
        var result = new OffHoursRule().evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
        assertThat(result.ruleName()).isEqualTo("OFF_HOURS");
        if (result.triggered()) {
            assertThat(result.scoreContribution()).isEqualTo(0.10);
            assertThat(result.description()).contains("unusual hour");
        } else {
            assertThat(result.scoreContribution()).isZero();
            assertThat(result.description()).contains("Normal hours");
        }
    }

    // ── InternationalWireRule ──────────────────────────────────────────────────

    @Test
    void internationalWireRule_swiftPayment_triggeredWithScore015() {
        var result = new InternationalWireRule().evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.SWIFT), paymentService);
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(0.15);
    }

    @Test
    void internationalWireRule_neftPayment_notTriggered() {
        assertThat(new InternationalWireRule().evaluate(
                buildPayment(BigDecimal.TEN, Payment.PaymentType.NEFT), paymentService).triggered()).isFalse();
    }

    @Test
    void internationalWireRule_impsPayment_notTriggered() {
        assertThat(new InternationalWireRule().evaluate(
                buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS), paymentService).triggered()).isFalse();
    }

    @Test
    void internationalWireRule_internalPayment_notTriggered() {
        assertThat(new InternationalWireRule().evaluate(
                buildPayment(BigDecimal.TEN, Payment.PaymentType.INTERNAL), paymentService).triggered()).isFalse();
    }

    @Test
    void internationalWireRule_upiPayment_notTriggered() {
        assertThat(new InternationalWireRule().evaluate(
                buildPayment(BigDecimal.TEN, Payment.PaymentType.UPI), paymentService).triggered()).isFalse();
    }

    // ── RoundAmountRule ────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "1000,   true",
        "5000,   true",
        "100000, true",
        "999,    false",
        "1001,   false",
        "5500,   false",
        "123.45, false"
    })
    void roundAmountRule_detectsRoundNumbers(double amount, boolean triggered) {
        var rule   = new RoundAmountRule();
        var result = rule.evaluate(buildPayment(BigDecimal.valueOf(amount), Payment.PaymentType.IMPS), paymentService);
        assertThat(result.triggered()).isEqualTo(triggered);
        assertThat(result.ruleName()).isEqualTo("ROUND_AMOUNT");
        assertThat(result.scoreContribution()).isEqualTo(triggered ? 0.08 : 0.0);
    }

    // ── DailyLimitRule ─────────────────────────────────────────────────────────

    @Test
    void dailyLimitRule_projectedTotalExceeds200k_triggered() {
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(dailySummary(new BigDecimal("150000")));
        var result = new DailyLimitRule().evaluate(buildPayment(new BigDecimal("60000"), Payment.PaymentType.SWIFT), paymentService);
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(0.25);
    }

    @Test
    void dailyLimitRule_projectedTotalExactly200k_notTriggered() {
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(dailySummary(new BigDecimal("100000")));
        assertThat(new DailyLimitRule().evaluate(
                buildPayment(new BigDecimal("100000"), Payment.PaymentType.NEFT), paymentService).triggered()).isFalse();
    }

    @Test
    void dailyLimitRule_projectedTotalBelow200k_notTriggered() {
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(dailySummary(new BigDecimal("50000")));
        assertThat(new DailyLimitRule().evaluate(
                buildPayment(new BigDecimal("50000"), Payment.PaymentType.IMPS), paymentService).triggered()).isFalse();
    }

    @Test
    void dailyLimitRule_noSpendingAndSmallPayment_notTriggered() {
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(dailySummary(BigDecimal.ZERO));
        assertThat(new DailyLimitRule().evaluate(
                buildPayment(new BigDecimal("1000"), Payment.PaymentType.IMPS), paymentService).triggered()).isFalse();
    }

    // ── FraudAnalysis.decide / .classify ──────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "0.00, APPROVE",
        "0.39, APPROVE",
        "0.40, HOLD_FOR_REVIEW",
        "0.69, HOLD_FOR_REVIEW",
        "0.70, BLOCK",
        "1.00, BLOCK"
    })
    void fraudAnalysis_decide_returnsCorrectDecision(double score, FraudAnalysis.FraudDecision expected) {
        assertThat(FraudAnalysis.decide(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "0.00, LOW",
        "0.24, LOW",
        "0.25, MEDIUM",
        "0.49, MEDIUM",
        "0.50, HIGH",
        "0.74, HIGH",
        "0.75, CRITICAL",
        "1.00, CRITICAL"
    })
    void fraudAnalysis_classify_returnsCorrectRiskLevel(double score, Payment.FraudRiskLevel expected) {
        assertThat(FraudAnalysis.classify(score)).isEqualTo(expected);
    }

    @Test
    void fraudAnalysis_isHighRisk_returnsTrue_forHighAndCritical() {
        var high     = new FraudAnalysis("P", 0.55, Payment.FraudRiskLevel.HIGH,
                List.of(), List.of(), FraudAnalysis.FraudDecision.HOLD_FOR_REVIEW, "", LocalDateTime.now());
        var critical = new FraudAnalysis("P", 0.90, Payment.FraudRiskLevel.CRITICAL,
                List.of(), List.of(), FraudAnalysis.FraudDecision.BLOCK, "", LocalDateTime.now());
        assertThat(high.isHighRisk()).isTrue();
        assertThat(critical.isHighRisk()).isTrue();
    }

    @Test
    void fraudAnalysis_isHighRisk_returnsFalse_forLowAndMedium() {
        var low    = new FraudAnalysis("P", 0.10, Payment.FraudRiskLevel.LOW,
                List.of(), List.of(), FraudAnalysis.FraudDecision.APPROVE, "", LocalDateTime.now());
        var medium = new FraudAnalysis("P", 0.35, Payment.FraudRiskLevel.MEDIUM,
                List.of(), List.of(), FraudAnalysis.FraudDecision.APPROVE, "", LocalDateTime.now());
        assertThat(low.isHighRisk()).isFalse();
        assertThat(medium.isHighRisk()).isFalse();
    }

    // ── FraudDetectionService ──────────────────────────────────────────────────

    @Test
    void fraudDetectionService_scoreExceeds1_clampedAt1AndDecisionIsBlock() {
        FraudRule r1      = (p, ps) -> new FraudRuleResult("R1", true, 0.60, "desc");
        FraudRule r2      = (p, ps) -> new FraudRuleResult("R2", true, 0.60, "desc");
        var service       = new FraudDetectionService(List.of(r1, r2), paymentService);
        Payment payment   = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
        when(paymentService.findPayment("PAY-001")).thenReturn(payment);

        FraudAnalysis result = service.analysePayment("PAY-001");

        assertThat(result.fraudScore()).isEqualTo(1.0);
        assertThat(result.decision()).isEqualTo(FraudAnalysis.FraudDecision.BLOCK);
    }

    @Test
    void fraudDetectionService_noRulesTriggered_approveWithLowRisk() {
        FraudRule safe  = (p, ps) -> new FraudRuleResult("SAFE", false, 0.0, "OK");
        var service     = new FraudDetectionService(List.of(safe), paymentService);
        Payment payment = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
        when(paymentService.findPayment("PAY-001")).thenReturn(payment);

        FraudAnalysis result = service.analysePayment("PAY-001");

        assertThat(result.fraudScore()).isZero();
        assertThat(result.decision()).isEqualTo(FraudAnalysis.FraudDecision.APPROVE);
        assertThat(result.riskLevel()).isEqualTo(Payment.FraudRiskLevel.LOW);
        assertThat(result.triggeredRules()).isEmpty();
    }

    @Test
    void fraudDetectionService_midScore_holdForReviewDecision() {
        FraudRule r  = (p, ps) -> new FraudRuleResult("MID", true, 0.55, "moderate");
        var service  = new FraudDetectionService(List.of(r), paymentService);
        Payment payment = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
        when(paymentService.findPayment("PAY-001")).thenReturn(payment);

        assertThat(service.analysePayment("PAY-001").decision())
                .isEqualTo(FraudAnalysis.FraudDecision.HOLD_FOR_REVIEW);
    }

    @Test
    void fraudDetectionService_analysePaymentDirect_doesNotHitRepository() {
        FraudRule safe = (p, ps) -> new FraudRuleResult("X", false, 0.0, "safe");
        var service    = new FraudDetectionService(List.of(safe), paymentService);
        Payment payment = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);

        FraudAnalysis result = service.analysePaymentDirect(payment);

        assertThat(result.paymentId()).isEqualTo("PAY-001");
        verify(paymentService, never()).findPayment(any());
    }

    @Test
    void fraudDetectionService_allRuleResults_includedEvenIfNotTriggered() {
        FraudRule on  = (p, ps) -> new FraudRuleResult("R_ON",  true,  0.20, "triggered");
        FraudRule off = (p, ps) -> new FraudRuleResult("R_OFF", false, 0.00, "safe");
        var service   = new FraudDetectionService(List.of(on, off), paymentService);
        when(paymentService.findPayment("PAY-001")).thenReturn(buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS));

        FraudAnalysis result = service.analysePayment("PAY-001");

        assertThat(result.ruleResults()).hasSize(2);
        assertThat(result.triggeredRules()).hasSize(1);
    }
}
