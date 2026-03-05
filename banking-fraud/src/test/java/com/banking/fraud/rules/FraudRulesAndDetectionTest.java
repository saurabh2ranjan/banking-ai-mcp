package com.banking.fraud.rules;

import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.rules.FraudRules.*;
import com.banking.fraud.service.FraudDetectionService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.DailySpendingSummary;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
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
@DisplayName("Fraud rules and detection")
class FraudRulesAndDetectionTest {

    @Mock PaymentService paymentService;

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    // ─── HighValueRule ────────────────────────────────────────────────────────

    @Nested @DisplayName("HighValueRule")
    class HighValueRuleTest {
        private HighValueRule rule;
        @BeforeEach void init() { rule = new HighValueRule(); }

        @ParameterizedTest(name = "{0} → triggered={1}, score={2}")
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
        void thresholds(double amount, boolean triggered, double score) {
            var result = rule.evaluate(buildPayment(BigDecimal.valueOf(amount), Payment.PaymentType.IMPS), paymentService);
            assertThat(result.triggered()).isEqualTo(triggered);
            assertThat(result.scoreContribution()).isEqualTo(score);
            assertThat(result.ruleName()).isEqualTo("HIGH_VALUE");
        }
    }

    // ─── VelocityRule ─────────────────────────────────────────────────────────

    @Nested @DisplayName("VelocityRule")
    class VelocityRuleTest {
        private VelocityRule rule;
        @BeforeEach void init() { rule = new VelocityRule(); }

        @Test void zeroPayments_notTriggered() {
            when(paymentService.getRecentPayments(anyString(), eq(1))).thenReturn(List.of());
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isFalse();
            assertThat(r.scoreContribution()).isZero();
        }

        @Test void fourPayments_notTriggered() {
            when(paymentService.getRecentPayments(anyString(), eq(1)))
                    .thenReturn(Collections.nCopies(4, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void fivePayments_highVelocity_score025() {
            when(paymentService.getRecentPayments(anyString(), eq(1)))
                    .thenReturn(Collections.nCopies(5, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isTrue();
            assertThat(r.scoreContribution()).isEqualTo(0.25);
        }

        @Test void tenPayments_criticalVelocity_score040() {
            when(paymentService.getRecentPayments(anyString(), eq(1)))
                    .thenReturn(Collections.nCopies(10, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isTrue();
            assertThat(r.scoreContribution()).isEqualTo(0.40);
        }

        @Test void fifteenPayments_stillCritical_score040() {
            when(paymentService.getRecentPayments(anyString(), eq(1)))
                    .thenReturn(Collections.nCopies(15, buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS)));
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.scoreContribution()).isEqualTo(0.40);
        }
    }

    // ─── OffHoursRule ─────────────────────────────────────────────────────────

    @Nested @DisplayName("OffHoursRule")
    class OffHoursRuleTest {
        private OffHoursRule rule;
        @BeforeEach void init() { rule = new OffHoursRule(); }

        @Test void currentHour_notOffHoursRange_mayReturnEitherResult() {
            // We can't control system clock so just assert result fields are coherent
            var r = rule.evaluate(buildPayment(BigDecimal.ONE, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.ruleName()).isEqualTo("OFF_HOURS");
            if (r.triggered()) {
                assertThat(r.scoreContribution()).isEqualTo(0.10);
                assertThat(r.description()).contains("unusual hour");
            } else {
                assertThat(r.scoreContribution()).isZero();
                assertThat(r.description()).contains("Normal hours");
            }
        }
    }

    // ─── InternationalWireRule ────────────────────────────────────────────────

    @Nested @DisplayName("InternationalWireRule")
    class InternationalWireRuleTest {
        private InternationalWireRule rule;
        @BeforeEach void init() { rule = new InternationalWireRule(); }

        @Test void swiftPayment_triggered_score015() {
            var r = rule.evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.SWIFT), paymentService);
            assertThat(r.triggered()).isTrue();
            assertThat(r.scoreContribution()).isEqualTo(0.15);
        }

        @Test void neftPayment_notTriggered() {
            var r = rule.evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.NEFT), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void impsPayment_notTriggered() {
            var r = rule.evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void internalPayment_notTriggered() {
            var r = rule.evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.INTERNAL), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void upiPayment_notTriggered() {
            var r = rule.evaluate(buildPayment(BigDecimal.TEN, Payment.PaymentType.UPI), paymentService);
            assertThat(r.triggered()).isFalse();
        }
    }

    // ─── RoundAmountRule ──────────────────────────────────────────────────────

    @Nested @DisplayName("RoundAmountRule")
    class RoundAmountRuleTest {
        private RoundAmountRule rule;
        @BeforeEach void init() { rule = new RoundAmountRule(); }

        @ParameterizedTest(name = "{0} → triggered={1}")
        @CsvSource({
            "1000,   true",
            "5000,   true",
            "100000, true",
            "999,    false",
            "1001,   false",
            "5500,   false",
            "123.45, false"
        })
        void roundnessDetection(double amount, boolean triggered) {
            var r = rule.evaluate(buildPayment(BigDecimal.valueOf(amount), Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isEqualTo(triggered);
            assertThat(r.ruleName()).isEqualTo("ROUND_AMOUNT");
            if (triggered) assertThat(r.scoreContribution()).isEqualTo(0.08);
            else assertThat(r.scoreContribution()).isZero();
        }
    }

    // ─── DailyLimitRule ───────────────────────────────────────────────────────

    @Nested @DisplayName("DailyLimitRule")
    class DailyLimitRuleTest {
        private DailyLimitRule rule;
        @BeforeEach void init() { rule = new DailyLimitRule(); }

        @Test void projectedTotalExceeds200k_triggered() {
            // 150k spent + 60k new = 210k > 200k threshold
            when(paymentService.getDailySpendingSummary("ACC-SRC"))
                    .thenReturn(dailySummary(new BigDecimal("150000")));
            var r = rule.evaluate(buildPayment(new BigDecimal("60000"), Payment.PaymentType.SWIFT), paymentService);
            assertThat(r.triggered()).isTrue();
            assertThat(r.scoreContribution()).isEqualTo(0.25);
        }

        @Test void projectedTotalExactly200k_notTriggered() {
            // 100k + 100k = 200k, not exceeding
            when(paymentService.getDailySpendingSummary("ACC-SRC"))
                    .thenReturn(dailySummary(new BigDecimal("100000")));
            var r = rule.evaluate(buildPayment(new BigDecimal("100000"), Payment.PaymentType.NEFT), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void projectedTotalBelow200k_notTriggered() {
            when(paymentService.getDailySpendingSummary("ACC-SRC"))
                    .thenReturn(dailySummary(new BigDecimal("50000")));
            var r = rule.evaluate(buildPayment(new BigDecimal("50000"), Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isFalse();
        }

        @Test void noSpendingToday_smallPayment_notTriggered() {
            when(paymentService.getDailySpendingSummary("ACC-SRC"))
                    .thenReturn(dailySummary(BigDecimal.ZERO));
            var r = rule.evaluate(buildPayment(new BigDecimal("1000"), Payment.PaymentType.IMPS), paymentService);
            assertThat(r.triggered()).isFalse();
        }
    }

    // ─── FraudAnalysis domain ─────────────────────────────────────────────────

    @Nested @DisplayName("FraudAnalysis — decide() and classify()")
    class FraudAnalysisDomainTest {

        @ParameterizedTest(name = "score={0} → {1}")
        @CsvSource({
            "0.00, APPROVE",
            "0.39, APPROVE",
            "0.40, HOLD_FOR_REVIEW",
            "0.69, HOLD_FOR_REVIEW",
            "0.70, BLOCK",
            "1.00, BLOCK"
        })
        void decide_thresholds(double score, FraudAnalysis.FraudDecision expected) {
            assertThat(FraudAnalysis.decide(score)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "score={0} → {1}")
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
        void classify_levels(double score, Payment.FraudRiskLevel expected) {
            assertThat(FraudAnalysis.classify(score)).isEqualTo(expected);
        }

        @Test void isHighRisk_true_forHighAndCritical() {
            var h = new FraudAnalysis("P", 0.55, Payment.FraudRiskLevel.HIGH,
                    List.of(), List.of(), FraudAnalysis.FraudDecision.HOLD_FOR_REVIEW, "", LocalDateTime.now());
            var c = new FraudAnalysis("P", 0.90, Payment.FraudRiskLevel.CRITICAL,
                    List.of(), List.of(), FraudAnalysis.FraudDecision.BLOCK, "", LocalDateTime.now());
            assertThat(h.isHighRisk()).isTrue();
            assertThat(c.isHighRisk()).isTrue();
        }

        @Test void isHighRisk_false_forLowAndMedium() {
            var l = new FraudAnalysis("P", 0.10, Payment.FraudRiskLevel.LOW,
                    List.of(), List.of(), FraudAnalysis.FraudDecision.APPROVE, "", LocalDateTime.now());
            var m = new FraudAnalysis("P", 0.35, Payment.FraudRiskLevel.MEDIUM,
                    List.of(), List.of(), FraudAnalysis.FraudDecision.APPROVE, "", LocalDateTime.now());
            assertThat(l.isHighRisk()).isFalse();
            assertThat(m.isHighRisk()).isFalse();
        }
    }

    // ─── FraudDetectionService ────────────────────────────────────────────────

    @Nested @DisplayName("FraudDetectionService — score clamping and rule aggregation")
    class FraudDetectionServiceTest {

        @Test @DisplayName("Score is clamped to 1.0 when rules sum exceeds 1.0")
        void scoreClampedAt1_whenMultipleRulesExceedMax() {
            // HIGH_VALUE critical (0.45) + VELOCITY critical (0.40) + SWIFT (0.15) + ROUND (0.08)
            // = 1.08 → clamped to 1.0
            FraudRule alwaysHigh = (p, ps) -> new FraudRuleResult("R1", true, 0.60, "desc");
            FraudRule alsoHigh   = (p, ps) -> new FraudRuleResult("R2", true, 0.60, "desc");
            var service = new FraudDetectionService(List.of(alwaysHigh, alsoHigh), paymentService);

            Payment p = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
            when(paymentService.findPayment("PAY-001")).thenReturn(p);

            FraudAnalysis result = service.analysePayment("PAY-001");

            assertThat(result.fraudScore()).isEqualTo(1.0);
            assertThat(result.decision()).isEqualTo(FraudAnalysis.FraudDecision.BLOCK);
        }

        @Test @DisplayName("No rules triggered → APPROVE with LOW risk")
        void noRulesTriggered_approve() {
            FraudRule safe = (p, ps) -> new FraudRuleResult("SAFE", false, 0.0, "OK");
            var service = new FraudDetectionService(List.of(safe), paymentService);

            Payment p = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
            when(paymentService.findPayment("PAY-001")).thenReturn(p);

            FraudAnalysis result = service.analysePayment("PAY-001");

            assertThat(result.fraudScore()).isZero();
            assertThat(result.decision()).isEqualTo(FraudAnalysis.FraudDecision.APPROVE);
            assertThat(result.riskLevel()).isEqualTo(Payment.FraudRiskLevel.LOW);
            assertThat(result.triggeredRules()).isEmpty();
        }

        @Test @DisplayName("Score 0.55 → HOLD_FOR_REVIEW with HIGH risk")
        void midScore_holdForReview() {
            FraudRule r = (p, ps) -> new FraudRuleResult("MID", true, 0.55, "moderate risk");
            var service = new FraudDetectionService(List.of(r), paymentService);

            Payment p = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
            when(paymentService.findPayment("PAY-001")).thenReturn(p);

            FraudAnalysis result = service.analysePayment("PAY-001");

            assertThat(result.decision()).isEqualTo(FraudAnalysis.FraudDecision.HOLD_FOR_REVIEW);
            assertThat(result.triggeredRules()).hasSize(1);
        }

        @Test @DisplayName("analysePaymentDirect — accepts Payment object directly")
        void analysePaymentDirect_worksWithoutRepository() {
            FraudRule r = (p, ps) -> new FraudRuleResult("X", false, 0.0, "safe");
            var service = new FraudDetectionService(List.of(r), paymentService);

            Payment p = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
            FraudAnalysis result = service.analysePaymentDirect(p);

            assertThat(result.paymentId()).isEqualTo("PAY-001");
            verify(paymentService, never()).findPayment(any());
        }

        @Test @DisplayName("All rule results are present in analysis even if not triggered")
        void allRuleResults_inAnalysis_evenIfNotTriggered() {
            FraudRule triggered = (p, ps) -> new FraudRuleResult("R_ON",  true,  0.20, "triggered");
            FraudRule safe      = (p, ps) -> new FraudRuleResult("R_OFF", false, 0.00, "safe");
            var service = new FraudDetectionService(List.of(triggered, safe), paymentService);

            Payment p = buildPayment(BigDecimal.TEN, Payment.PaymentType.IMPS);
            when(paymentService.findPayment("PAY-001")).thenReturn(p);

            FraudAnalysis result = service.analysePayment("PAY-001");
            assertThat(result.ruleResults()).hasSize(2);
            assertThat(result.triggeredRules()).hasSize(1); // only triggered ones
        }
    }
}
