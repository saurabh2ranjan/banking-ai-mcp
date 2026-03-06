package com.banking.payment.dto;

import com.banking.payment.domain.Payment;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDtos {

    public record InitiatePaymentRequest(
        @NotBlank String customerId,
        @NotBlank String sourceAccountId,
        @NotBlank String destinationAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min=3, max=3) String currency,
        @NotNull Payment.PaymentType paymentType,
        @Size(max = 500) String description
    ) {}

    public record PaymentResponse(
        String paymentId,
        String referenceNumber,
        String customerId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String paymentType,
        String status,
        String description,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt,
        String failureReason,
        BigDecimal fraudScore,
        String fraudRiskLevel
    ) {}

    public record PaymentSummary(
        String paymentId,
        String referenceNumber,
        BigDecimal amount,
        String currency,
        String paymentType,
        String status,
        LocalDateTime initiatedAt
    ) {}

    public record DailySpendingSummary(
        String accountId,
        BigDecimal totalSpentToday,
        int transactionCount,
        BigDecimal averageTransactionSize,
        BigDecimal largestTransaction,
        String currency
    ) {}
}
