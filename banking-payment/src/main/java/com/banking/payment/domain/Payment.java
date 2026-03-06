package com.banking.payment.domain;

import com.banking.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_source",      columnList = "source_account_id"),
        @Index(name = "idx_payment_dest",        columnList = "destination_account_id"),
        @Index(name = "idx_payment_status",      columnList = "status"),
        @Index(name = "idx_payment_reference",   columnList = "reference_number", unique = true),
        @Index(name = "idx_payment_customer",    columnList = "customer_id"),
        @Index(name = "idx_payment_initiated_at",columnList = "initiated_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "reference_number", nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;

    @Column(name = "source_account_id", nullable = false, length = 30)
    private String sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, length = 30)
    private String destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "fraud_score", precision = 4, scale = 3)
    private BigDecimal fraudScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_risk_level", length = 10)
    private FraudRiskLevel fraudRiskLevel;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_fingerprint", length = 200)
    private String deviceFingerprint;

    @Column(name = "reversal_payment_id", length = 36)
    private String reversalPaymentId;    // Set when this payment is reversed

    @PrePersist
    protected void onCreate() { initiatedAt = LocalDateTime.now(); }

    public enum PaymentType   { NEFT, RTGS, IMPS, UPI, SWIFT, INTERNAL, STANDING_ORDER }
    public enum PaymentStatus { INITIATED, PENDING_FRAUD_CHECK, FRAUD_HOLD, PROCESSING, COMPLETED, FAILED, REVERSED, CANCELLED }
    public enum FraudRiskLevel{ LOW, MEDIUM, HIGH, CRITICAL }

    public boolean isTerminal() {
        return status == PaymentStatus.COMPLETED
            || status == PaymentStatus.FAILED
            || status == PaymentStatus.REVERSED
            || status == PaymentStatus.CANCELLED;
    }
}
