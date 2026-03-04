package com.banking.payment.mcp;

import com.banking.common.exception.BankingExceptions.BankingException;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMcpTool {

    private final PaymentService paymentService;

    @Tool(name = "initiate_payment",
          description = "Initiate a bank payment. Funds are placed on hold immediately. " +
                        "Payment enters PENDING_FRAUD_CHECK and must pass fraud analysis before processing. " +
                        "Types: NEFT, RTGS, IMPS, UPI, SWIFT, INTERNAL, STANDING_ORDER.")
    public Map<String, Object> initiatePayment(
            @ToolParam(description = "Customer ID initiating the payment") String customerId,
            @ToolParam(description = "Source account ID") String sourceAccountId,
            @ToolParam(description = "Destination account ID") String destinationAccountId,
            @ToolParam(description = "Payment amount") double amount,
            @ToolParam(description = "Currency code e.g. USD, GBP, INR") String currency,
            @ToolParam(description = "Payment type: NEFT, RTGS, IMPS, UPI, SWIFT, INTERNAL") String paymentType,
            @ToolParam(description = "Payment description or purpose") String description) {
        try {
            InitiatePaymentRequest req = new InitiatePaymentRequest(
                customerId, sourceAccountId, destinationAccountId,
                BigDecimal.valueOf(amount), currency,
                Payment.PaymentType.valueOf(paymentType.toUpperCase()), description
            );
            PaymentResponse p = paymentService.initiatePayment(req);
            return Map.of(
                "paymentId",       p.paymentId(),
                "referenceNumber", p.referenceNumber(),
                "status",          p.status(),
                "amount",          p.amount(),
                "currency",        p.currency(),
                "message",         "Payment initiated. Funds on hold. Run fraud check before processing."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid payment type. Valid: NEFT, RTGS, IMPS, UPI, SWIFT, INTERNAL");
        }
    }

    @Tool(name = "process_payment",
          description = "Execute a payment that has passed fraud check. " +
                        "Debits source account and credits destination. Payment must be in PENDING_FRAUD_CHECK state.")
    public Map<String, Object> processPayment(
            @ToolParam(description = "Payment ID to process") String paymentId) {
        try {
            PaymentResponse p = paymentService.processPayment(paymentId);
            return Map.of(
                "paymentId",       p.paymentId(),
                "referenceNumber", p.referenceNumber(),
                "status",          p.status(),
                "completedAt",     p.completedAt().toString(),
                "amount",          p.amount(),
                "currency",        p.currency(),
                "message",         "Payment completed successfully."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(name = "get_payment_status",
          description = "Retrieve the full details and current status of a payment by payment ID or reference number.")
    public Map<String, Object> getPaymentStatus(
            @ToolParam(description = "Payment ID to look up") String paymentId) {
        try {
            PaymentResponse p = paymentService.getPayment(paymentId);
            return Map.ofEntries(
                Map.entry("paymentId",             p.paymentId()),
                Map.entry("referenceNumber",       p.referenceNumber()),
                Map.entry("sourceAccountId",       p.sourceAccountId()),
                Map.entry("destinationAccountId",  p.destinationAccountId()),
                Map.entry("amount",                p.amount()),
                Map.entry("currency",              p.currency()),
                Map.entry("paymentType",           p.paymentType()),
                Map.entry("status",                p.status()),
                Map.entry("description",           p.description() != null ? p.description() : "N/A"),
                Map.entry("initiatedAt",           p.initiatedAt().toString()),
                Map.entry("fraudScore",            p.fraudScore() != null ? p.fraudScore() : "N/A"),
                Map.entry("fraudRiskLevel",        p.fraudRiskLevel() != null ? p.fraudRiskLevel() : "N/A"),
                Map.entry("failureReason",         p.failureReason() != null ? p.failureReason() : "N/A")
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "get_payment_history",
          description = "Get paginated payment history for an account with most recent first.")
    public Map<String, Object> getPaymentHistory(
            @ToolParam(description = "Account ID to fetch payment history for") String accountId,
            @ToolParam(description = "Page number (0-indexed)") int page,
            @ToolParam(description = "Page size (max 20)") int size) {
        var results = paymentService.getAccountPayments(
            accountId, PageRequest.of(page, Math.min(size, 20)));
        return Map.of(
            "accountId",    accountId,
            "totalPayments",results.getTotalElements(),
            "totalPages",   results.getTotalPages(),
            "page",         page,
            "payments", results.getContent().stream()
                .map(p -> Map.of(
                    "paymentId",       p.paymentId(),
                    "referenceNumber", p.referenceNumber(),
                    "amount",          p.amount(),
                    "currency",        p.currency(),
                    "type",            p.paymentType(),
                    "status",          p.status(),
                    "initiatedAt",     p.initiatedAt().toString()
                ))
                .collect(Collectors.toList())
        );
    }

    @Tool(name = "hold_payment_for_fraud",
          description = "Place a payment on FRAUD_HOLD. The held funds are not released until human review. " +
                        "Use this when fraud score is between 0.40 and 0.70.")
    public Map<String, Object> holdPaymentForFraud(
            @ToolParam(description = "Payment ID to hold") String paymentId,
            @ToolParam(description = "Fraud score (0.0 = safe, 1.0 = certain fraud)") double fraudScore,
            @ToolParam(description = "Risk level: LOW, MEDIUM, HIGH, CRITICAL") String riskLevel,
            @ToolParam(description = "Reason for fraud hold") String reason) {
        try {
            Payment.FraudRiskLevel level = Payment.FraudRiskLevel.valueOf(riskLevel.toUpperCase());
            PaymentResponse p = paymentService.holdForFraud(paymentId, fraudScore, level, reason);
            return Map.of(
                "paymentId",  p.paymentId(),
                "status",     p.status(),
                "fraudScore", fraudScore,
                "riskLevel",  riskLevel,
                "message",    "Payment on fraud hold. Compliance team notified. Awaiting human review."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "reverse_payment",
          description = "Reverse a completed payment. Money is returned to the sender. " +
                        "Only COMPLETED payments can be reversed.")
    public Map<String, Object> reversePayment(
            @ToolParam(description = "Payment ID to reverse") String paymentId,
            @ToolParam(description = "Reason for reversal") String reason) {
        try {
            PaymentResponse p = paymentService.reversePayment(paymentId, reason);
            return Map.of(
                "reversalPaymentId",  p.paymentId(),
                "originalPaymentId",  paymentId,
                "status",             p.status(),
                "amount",             p.amount(),
                "message",            "Payment reversed. Funds returned to source account."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(name = "get_daily_spending_summary",
          description = "Get a breakdown of today's spending from an account: total, count, average, and largest transaction.")
    public Map<String, Object> getDailySpendingSummary(
            @ToolParam(description = "Account ID to summarise") String accountId) {
        DailySpendingSummary s = paymentService.getDailySpendingSummary(accountId);
        return Map.of(
            "accountId",            s.accountId(),
            "totalSpentToday",      s.totalSpentToday(),
            "transactionCount",     s.transactionCount(),
            "averageTransactionSize",s.averageTransactionSize(),
            "largestTransaction",   s.largestTransaction(),
            "currency",             s.currency()
        );
    }
}
