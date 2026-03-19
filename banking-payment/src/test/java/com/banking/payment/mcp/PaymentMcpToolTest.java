package com.banking.payment.mcp;

import com.banking.common.exception.BankingExceptions.InsufficientFundsException;
import com.banking.common.exception.BankingExceptions.PaymentNotFoundException;
import com.banking.common.exception.BankingExceptions.PaymentException;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.domain.Payment;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMcpToolTest {

    @Mock        PaymentService paymentService;
    @InjectMocks PaymentMcpTool paymentMcpTool;

    // ── helpers ───────────────────────────────────────────────────────────────

    private PaymentResponse paymentResp(String paymentId, String status) {
        return new PaymentResponse(
            paymentId, "IMPS-REF-001", "CUST-001",
            "ACC-SRC", "ACC-DST",
            new BigDecimal("500.00"), "GBP",
            "IMPS", status, "Rent payment",
            LocalDateTime.now(),
            "COMPLETED".equals(status) ? LocalDateTime.now() : null,
            null, null, null
        );
    }

    private PaymentSummary paymentSummary(String paymentId) {
        return new PaymentSummary(
            paymentId, "REF-001",
            new BigDecimal("500.00"), "GBP",
            "IMPS", "COMPLETED", LocalDateTime.now()
        );
    }

    // ── initiate_payment ──────────────────────────────────────────────────────

    @Test
    void initiatePayment_validRequest_returnsPaymentDetails() {
        when(paymentService.initiatePayment(any()))
                .thenReturn(paymentResp("PAY-001", "PENDING_FRAUD_CHECK"));

        Map<String, Object> result = paymentMcpTool.initiatePayment(
                "CUST-001", "ACC-SRC", "ACC-DST",
                500.0, "GBP", "IMPS", "Rent payment");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("paymentId")).isEqualTo("PAY-001");
        assertThat(result.get("referenceNumber")).isEqualTo("IMPS-REF-001");
        assertThat(result.get("status")).isEqualTo("PENDING_FRAUD_CHECK");
        assertThat(result.get("currency")).isEqualTo("GBP");
        assertThat(result.get("message").toString()).contains("fraud check");
    }

    @Test
    void initiatePayment_lowercasePaymentType_isAccepted() {
        when(paymentService.initiatePayment(any()))
                .thenReturn(paymentResp("PAY-001", "PENDING_FRAUD_CHECK"));

        Map<String, Object> result = paymentMcpTool.initiatePayment(
                "CUST-001", "ACC-SRC", "ACC-DST",
                500.0, "GBP", "imps", "Rent payment");  // lowercase

        assertThat(result).doesNotContainKey("error");
    }

    @Test
    void initiatePayment_invalidPaymentType_returnsError() {
        Map<String, Object> result = paymentMcpTool.initiatePayment(
                "CUST-001", "ACC-SRC", "ACC-DST",
                500.0, "GBP", "INVALID_TYPE", "Rent payment");

        assertThat(result).containsKey("error");
        assertThat(result.get("error").toString()).contains("Invalid payment type");
        verifyNoInteractions(paymentService);
    }

    @Test
    void initiatePayment_insufficientFunds_returnsErrorWithCode() {
        when(paymentService.initiatePayment(any()))
                .thenThrow(new InsufficientFundsException("ACC-SRC"));

        Map<String, Object> result = paymentMcpTool.initiatePayment(
                "CUST-001", "ACC-SRC", "ACC-DST",
                500.0, "GBP", "IMPS", "Rent payment");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    // ── process_payment ───────────────────────────────────────────────────────

    @Test
    void processPayment_validPayment_returnsCompletedDetails() {
        when(paymentService.processPayment("PAY-001"))
                .thenReturn(paymentResp("PAY-001", "COMPLETED"));

        Map<String, Object> result = paymentMcpTool.processPayment("PAY-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("paymentId")).isEqualTo("PAY-001");
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(result.get("completedAt")).isNotNull();
        assertThat(result.get("amount")).isEqualTo(new BigDecimal("500.00"));
        assertThat(result.get("message").toString()).contains("completed successfully");
    }

    @Test
    void processPayment_paymentNotFound_returnsErrorWithCode() {
        when(paymentService.processPayment("GHOST"))
                .thenThrow(new PaymentNotFoundException("GHOST"));

        Map<String, Object> result = paymentMcpTool.processPayment("GHOST");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void processPayment_alreadyProcessed_returnsErrorWithCode() {
        when(paymentService.processPayment("PAY-001"))
                .thenThrow(new PaymentException("Payment already processed"));

        Map<String, Object> result = paymentMcpTool.processPayment("PAY-001");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    // ── get_payment_status ────────────────────────────────────────────────────

    @Test
    void getPaymentStatus_existingPayment_returnsFullDetails() {
        when(paymentService.getPayment("PAY-001"))
                .thenReturn(paymentResp("PAY-001", "COMPLETED"));

        Map<String, Object> result = paymentMcpTool.getPaymentStatus("PAY-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("paymentId")).isEqualTo("PAY-001");
        assertThat(result.get("sourceAccountId")).isEqualTo("ACC-SRC");
        assertThat(result.get("destinationAccountId")).isEqualTo("ACC-DST");
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(result.get("currency")).isEqualTo("GBP");
        assertThat(result).containsKeys(
            "paymentId", "referenceNumber", "sourceAccountId", "destinationAccountId",
            "amount", "currency", "paymentType", "status", "description",
            "initiatedAt", "fraudScore", "fraudRiskLevel", "failureReason"
        );
    }

    @Test
    void getPaymentStatus_nullOptionalFields_returnsNA() {
        // paymentResp already has null completedAt, fraudScore, fraudRiskLevel, failureReason
        when(paymentService.getPayment("PAY-001"))
                .thenReturn(paymentResp("PAY-001", "PENDING_FRAUD_CHECK"));

        Map<String, Object> result = paymentMcpTool.getPaymentStatus("PAY-001");

        assertThat(result.get("fraudScore")).isEqualTo("N/A");
        assertThat(result.get("fraudRiskLevel")).isEqualTo("N/A");
        assertThat(result.get("failureReason")).isEqualTo("N/A");
    }

    @Test
    void getPaymentStatus_notFound_returnsError() {
        when(paymentService.getPayment("GHOST"))
                .thenThrow(new PaymentNotFoundException("GHOST"));

        Map<String, Object> result = paymentMcpTool.getPaymentStatus("GHOST");

        assertThat(result).containsKey("error");
        // note: getPaymentStatus only returns "error", not "errorCode"
        assertThat(result).doesNotContainKey("errorCode");
    }

    // ── get_payment_history ───────────────────────────────────────────────────

    @Test
    void getPaymentHistory_returnsPaginatedResults() {
        List<PaymentSummary> summaries = List.of(
            paymentSummary("PAY-001"),
            paymentSummary("PAY-002")
        );
        when(paymentService.getAccountPayments(eq("ACC-SRC"), any()))
                .thenReturn(new PageImpl<>(summaries, PageRequest.of(0, 10), 2));

        Map<String, Object> result = paymentMcpTool.getPaymentHistory("ACC-SRC", 0, 10);

        assertThat(result.get("accountId")).isEqualTo("ACC-SRC");
        assertThat(result.get("totalPayments")).isEqualTo(2L);
        assertThat(result.get("totalPages")).isEqualTo(1);
        assertThat(result.get("page")).isEqualTo(0);
        List<?> payments = (List<?>) result.get("payments");
        assertThat(payments).hasSize(2);
    }

    @Test
    void getPaymentHistory_noPayments_returnsEmptyList() {
        when(paymentService.getAccountPayments(eq("ACC-NEW"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Object> result = paymentMcpTool.getPaymentHistory("ACC-NEW", 0, 10);

        assertThat(result.get("totalPayments")).isEqualTo(0L);
        assertThat((List<?>) result.get("payments")).isEmpty();
    }

    @Test
    void getPaymentHistory_sizeExceeds20_isCappedAt20() {
        when(paymentService.getAccountPayments(eq("ACC-SRC"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        paymentMcpTool.getPaymentHistory("ACC-SRC", 0, 100);

        verify(paymentService).getAccountPayments("ACC-SRC", PageRequest.of(0, 20));
    }

    @Test
    void getPaymentHistory_paymentMapContainsExpectedFields() {
        when(paymentService.getAccountPayments(eq("ACC-SRC"), any()))
                .thenReturn(new PageImpl<>(List.of(paymentSummary("PAY-001"))));

        Map<String, Object> result = paymentMcpTool.getPaymentHistory("ACC-SRC", 0, 10);

        List<?> payments = (List<?>) result.get("payments");
        Map<?, ?> first = (Map<?, ?>) payments.get(0);
        assertThat(first.get("paymentId")).isEqualTo("PAY-001");
        assertThat(first.get("referenceNumber")).isEqualTo("REF-001");
        assertThat(first.get("amount")).isEqualTo(new BigDecimal("500.00"));
        assertThat(first.get("currency")).isEqualTo("GBP");
        assertThat(first.get("type")).isEqualTo("IMPS");
        assertThat(first.get("status")).isEqualTo("COMPLETED");
        assertThat(first.get("initiatedAt")).isNotNull();
    }

    // ── hold_payment_for_fraud ────────────────────────────────────────────────

    @Test
    void holdPaymentForFraud_validRequest_returnsFraudHoldStatus() {
        when(paymentService.holdForFraud("PAY-001", 0.55,
                Payment.FraudRiskLevel.HIGH, "Suspicious pattern"))
                .thenReturn(paymentResp("PAY-001", "FRAUD_HOLD"));

        Map<String, Object> result = paymentMcpTool.holdPaymentForFraud(
                "PAY-001", 0.55, "HIGH", "Suspicious pattern");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("paymentId")).isEqualTo("PAY-001");
        assertThat(result.get("status")).isEqualTo("FRAUD_HOLD");
        assertThat(result.get("fraudScore")).isEqualTo(0.55);
        assertThat(result.get("riskLevel")).isEqualTo("HIGH");
        assertThat(result.get("message").toString()).contains("human review");
    }

    @Test
    void holdPaymentForFraud_lowercaseRiskLevel_isAccepted() {
        when(paymentService.holdForFraud(any(), anyDouble(), any(), any()))
                .thenReturn(paymentResp("PAY-001", "FRAUD_HOLD"));

        Map<String, Object> result = paymentMcpTool.holdPaymentForFraud(
                "PAY-001", 0.55, "high", "Suspicious pattern");  // lowercase

        assertThat(result).doesNotContainKey("error");
    }

    @Test
    void holdPaymentForFraud_paymentNotFound_returnsError() {
        when(paymentService.holdForFraud(eq("GHOST"), anyDouble(), any(), any()))
                .thenThrow(new PaymentNotFoundException("GHOST"));

        Map<String, Object> result = paymentMcpTool.holdPaymentForFraud(
                "GHOST", 0.55, "HIGH", "Suspicious pattern");

        assertThat(result).containsKey("error");
        // note: holdPaymentForFraud only returns "error", not "errorCode"
        assertThat(result).doesNotContainKey("errorCode");
    }

    // ── reverse_payment ───────────────────────────────────────────────────────

    @Test
    void reversePayment_completedPayment_returnsReversalDetails() {
        when(paymentService.reversePayment("PAY-001", "Customer dispute"))
                .thenReturn(paymentResp("PAY-REV-001", "REVERSED"));

        Map<String, Object> result = paymentMcpTool.reversePayment("PAY-001", "Customer dispute");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("reversalPaymentId")).isEqualTo("PAY-REV-001");
        assertThat(result.get("originalPaymentId")).isEqualTo("PAY-001");
        assertThat(result.get("status")).isEqualTo("REVERSED");
        assertThat(result.get("amount")).isEqualTo(new BigDecimal("500.00"));
        assertThat(result.get("message").toString()).contains("Funds returned");
    }

    @Test
    void reversePayment_nonCompletedPayment_returnsErrorWithCode() {
        when(paymentService.reversePayment("PAY-001", "reason"))
                .thenThrow(new PaymentException("Only COMPLETED payments can be reversed"));

        Map<String, Object> result = paymentMcpTool.reversePayment("PAY-001", "reason");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void reversePayment_notFound_returnsErrorWithCode() {
        when(paymentService.reversePayment("GHOST", "reason"))
                .thenThrow(new PaymentNotFoundException("GHOST"));

        Map<String, Object> result = paymentMcpTool.reversePayment("GHOST", "reason");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    // ── get_daily_spending_summary ────────────────────────────────────────────

    @Test
    void getDailySpendingSummary_returnsFullSummary() {
        DailySpendingSummary summary = new DailySpendingSummary(
            "ACC-SRC",
            new BigDecimal("3000.00"), 5,
            new BigDecimal("600.00"),
            new BigDecimal("1500.00"), "GBP"
        );
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(summary);

        Map<String, Object> result = paymentMcpTool.getDailySpendingSummary("ACC-SRC");

        assertThat(result.get("accountId")).isEqualTo("ACC-SRC");
        assertThat(result.get("totalSpentToday")).isEqualTo(new BigDecimal("3000.00"));
        assertThat(result.get("transactionCount")).isEqualTo(5);
        assertThat(result.get("averageTransactionSize")).isEqualTo(new BigDecimal("600.00"));
        assertThat(result.get("largestTransaction")).isEqualTo(new BigDecimal("1500.00"));
        assertThat(result.get("currency")).isEqualTo("GBP");
    }

    @Test
    void getDailySpendingSummary_noTransactionsToday_returnsZeroValues() {
        DailySpendingSummary summary = new DailySpendingSummary(
            "ACC-NEW",
            BigDecimal.ZERO, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, "GBP"
        );
        when(paymentService.getDailySpendingSummary("ACC-NEW")).thenReturn(summary);

        Map<String, Object> result = paymentMcpTool.getDailySpendingSummary("ACC-NEW");

        assertThat(result.get("totalSpentToday")).isEqualTo(BigDecimal.ZERO);
        assertThat(result.get("transactionCount")).isEqualTo(0);
    }
}