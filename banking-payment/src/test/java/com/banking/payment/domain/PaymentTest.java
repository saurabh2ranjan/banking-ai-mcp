package com.banking.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payment — domain entity")
class PaymentTest {

    private Payment buildPayment(Payment.PaymentStatus status) {
        return Payment.builder()
                .paymentId("PAY-001")
                .referenceNumber("IMPS-123456-ABCDEF")
                .customerId("CUST-001")
                .sourceAccountId("ACC-SRC")
                .destinationAccountId("ACC-DST")
                .amount(new BigDecimal("500.00"))
                .currency("GBP")
                .paymentType(Payment.PaymentType.IMPS)
                .status(status)
                .description("Test payment")
                .initiatedAt(LocalDateTime.now())
                .build();
    }

    // ─── isTerminal ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isTerminal()")
    class IsTerminal {

        @ParameterizedTest(name = "status={0} should be terminal")
        @EnumSource(value = Payment.PaymentStatus.class,
                    names = {"COMPLETED", "FAILED", "REVERSED", "CANCELLED"})
        void terminalStatuses_returnTrue(Payment.PaymentStatus status) {
            assertThat(buildPayment(status).isTerminal()).isTrue();
        }

        @ParameterizedTest(name = "status={0} should not be terminal")
        @EnumSource(value = Payment.PaymentStatus.class,
                    names = {"INITIATED", "PENDING_FRAUD_CHECK", "FRAUD_HOLD", "PROCESSING"})
        void nonTerminalStatuses_returnFalse(Payment.PaymentStatus status) {
            assertThat(buildPayment(status).isTerminal()).isFalse();
        }
    }

    // ─── Enum coverage ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enum values")
    class EnumValues {

        @Test
        void paymentTypes_allPresent() {
            assertThat(Payment.PaymentType.values())
                    .containsExactlyInAnyOrder(
                            Payment.PaymentType.NEFT,
                            Payment.PaymentType.RTGS,
                            Payment.PaymentType.IMPS,
                            Payment.PaymentType.UPI,
                            Payment.PaymentType.SWIFT,
                            Payment.PaymentType.INTERNAL,
                            Payment.PaymentType.STANDING_ORDER);
        }

        @Test
        void paymentStatuses_allPresent() {
            assertThat(Payment.PaymentStatus.values())
                    .containsExactlyInAnyOrder(
                            Payment.PaymentStatus.INITIATED,
                            Payment.PaymentStatus.PENDING_FRAUD_CHECK,
                            Payment.PaymentStatus.FRAUD_HOLD,
                            Payment.PaymentStatus.PROCESSING,
                            Payment.PaymentStatus.COMPLETED,
                            Payment.PaymentStatus.FAILED,
                            Payment.PaymentStatus.REVERSED,
                            Payment.PaymentStatus.CANCELLED);
        }

        @Test
        void fraudRiskLevels_allPresent() {
            assertThat(Payment.FraudRiskLevel.values())
                    .containsExactlyInAnyOrder(
                            Payment.FraudRiskLevel.LOW,
                            Payment.FraudRiskLevel.MEDIUM,
                            Payment.FraudRiskLevel.HIGH,
                            Payment.FraudRiskLevel.CRITICAL);
        }
    }

    // ─── Builder fields preserved ─────────────────────────────────────────────

    @Test
    @DisplayName("Builder sets all fields correctly")
    void builder_setsAllFields() {
        Payment p = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
        assertThat(p.getPaymentId()).isEqualTo("PAY-001");
        assertThat(p.getReferenceNumber()).isEqualTo("IMPS-123456-ABCDEF");
        assertThat(p.getCustomerId()).isEqualTo("CUST-001");
        assertThat(p.getSourceAccountId()).isEqualTo("ACC-SRC");
        assertThat(p.getDestinationAccountId()).isEqualTo("ACC-DST");
        assertThat(p.getAmount()).isEqualByComparingTo("500.00");
        assertThat(p.getCurrency()).isEqualTo("GBP");
        assertThat(p.getPaymentType()).isEqualTo(Payment.PaymentType.IMPS);
        assertThat(p.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
        assertThat(p.getDescription()).isEqualTo("Test payment");
        assertThat(p.getInitiatedAt()).isNotNull();
    }

    // ─── Mutable fraud fields ─────────────────────────────────────────────────

    @Test
    @DisplayName("Fraud score and risk level can be set after creation")
    void fraudFieldsCanBeMutated() {
        Payment p = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
        p.setFraudScore(BigDecimal.valueOf(0.72));
        p.setFraudRiskLevel(Payment.FraudRiskLevel.CRITICAL);
        p.setStatus(Payment.PaymentStatus.FRAUD_HOLD);
        p.setFailureReason("Blocked: critical score");

        assertThat(p.getFraudScore()).isEqualByComparingTo(BigDecimal.valueOf(0.72));
        assertThat(p.getFraudRiskLevel()).isEqualTo(Payment.FraudRiskLevel.CRITICAL);
        assertThat(p.getStatus()).isEqualTo(Payment.PaymentStatus.FRAUD_HOLD);
        assertThat(p.getFailureReason()).isEqualTo("Blocked: critical score");
    }

    // ─── Reversal link ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reversal payment ID can be linked after reversal")
    void reversalPaymentId_canBeLinked() {
        Payment p = buildPayment(Payment.PaymentStatus.COMPLETED);
        p.setStatus(Payment.PaymentStatus.REVERSED);
        p.setReversalPaymentId("PAY-REV-001");
        assertThat(p.getReversalPaymentId()).isEqualTo("PAY-REV-001");
        assertThat(p.isTerminal()).isTrue();
    }
}
