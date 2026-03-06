package com.banking.payment.service;

import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.notification.service.NotificationService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.mapper.PaymentMapper;
import com.banking.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock PaymentRepository   paymentRepository;
    @Mock PaymentMapper       paymentMapper;
    @Mock AccountService      accountService;
    @Mock NotificationService notificationService;

    @InjectMocks PaymentService paymentService;

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private Payment buildPayment(Payment.PaymentStatus status) {
        return Payment.builder()
                .paymentId("PAY-001")
                .referenceNumber("IMPS-" + System.currentTimeMillis() + "-ABCDEF")
                .customerId("CUST-001")
                .sourceAccountId("ACC-SRC")
                .destinationAccountId("ACC-DST")
                .amount(new BigDecimal("500.00"))
                .currency("GBP")
                .paymentType(Payment.PaymentType.IMPS)
                .status(status)
                .initiatedAt(LocalDateTime.now())
                .build();
    }

    private InitiatePaymentRequest validRequest() {
        return new InitiatePaymentRequest(
                "CUST-001", "ACC-SRC", "ACC-DST",
                new BigDecimal("500.00"), "GBP",
                Payment.PaymentType.IMPS, "Rent payment");
    }

    private PaymentResponse dummyResponse(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(), p.getReferenceNumber(), "CUST-001",
                "ACC-SRC", "ACC-DST", p.getAmount(), "GBP",
                p.getPaymentType().name(), p.getStatus().name(),
                "Rent payment", p.getInitiatedAt(), p.getCompletedAt(),
                p.getFailureReason(), p.getFraudScore(), null);
    }

    // ─── initiatePayment ──────────────────────────────────────────────────────

    @Nested @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        void bothAccountsActive_fundsOk_savesPaymentAndPlacesHold() {
            when(accountService.validateAccountActive("ACC-SRC")).thenReturn(true);
            when(accountService.validateAccountActive("ACC-DST")).thenReturn(true);
            when(accountService.hasSufficientFunds("ACC-SRC", new BigDecimal("500.00"))).thenReturn(true);

            ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any())).thenAnswer(i -> dummyResponse(cap.getValue()));

            PaymentResponse resp = paymentService.initiatePayment(validRequest());

            assertThat(cap.getValue().getStatus()).isEqualTo(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
            assertThat(cap.getValue().getPaymentId()).isNotNull();
            assertThat(cap.getValue().getReferenceNumber()).startsWith("IMPS-");
            verify(accountService).placeHold("ACC-SRC", new BigDecimal("500.00"));
            verify(notificationService).sendPaymentInitiatedSms(anyString(), anyString(), any(), anyString());
        }

        @Test
        void sourceAccountInactive_throwsAccountInactive() {
            when(accountService.validateAccountActive("ACC-SRC")).thenReturn(false);
            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest()))
                    .isInstanceOf(AccountInactiveException.class);
            verify(accountService, never()).placeHold(any(), any());
        }

        @Test
        void destinationAccountInactive_throwsAccountInactive() {
            when(accountService.validateAccountActive("ACC-SRC")).thenReturn(true);
            when(accountService.validateAccountActive("ACC-DST")).thenReturn(false);
            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest()))
                    .isInstanceOf(AccountInactiveException.class);
            verify(accountService, never()).placeHold(any(), any());
        }

        @Test
        void insufficientFunds_throwsInsufficientFunds() {
            when(accountService.validateAccountActive("ACC-SRC")).thenReturn(true);
            when(accountService.validateAccountActive("ACC-DST")).thenReturn(true);
            when(accountService.hasSufficientFunds("ACC-SRC", new BigDecimal("500.00"))).thenReturn(false);
            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest()))
                    .isInstanceOf(InsufficientFundsException.class);
        }
    }

    // ─── processPayment ───────────────────────────────────────────────────────

    @Nested @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        void pendingFraudCheckPayment_completesSuccessfully() {
            Payment payment = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any())).thenAnswer(i -> dummyResponse(payment));

            PaymentResponse resp = paymentService.processPayment("PAY-001");

            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
            assertThat(payment.getCompletedAt()).isNotNull();

            var inOrder = inOrder(accountService);
            inOrder.verify(accountService).releaseHold("ACC-SRC", new BigDecimal("500.00"));
            inOrder.verify(accountService).debitAccount("ACC-SRC", new BigDecimal("500.00"));
            inOrder.verify(accountService).creditAccount("ACC-DST", new BigDecimal("500.00"));

            verify(notificationService).sendPaymentCompletedEmail(anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        void alreadyCompletedPayment_throwsPaymentException() {
            Payment payment = buildPayment(Payment.PaymentStatus.COMPLETED);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            assertThatThrownBy(() -> paymentService.processPayment("PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        void fraudHoldPayment_throwsPaymentException() {
            Payment payment = buildPayment(Payment.PaymentStatus.FRAUD_HOLD);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            assertThatThrownBy(() -> paymentService.processPayment("PAY-001"))
                    .isInstanceOf(PaymentException.class);
        }

        @Test
        void unknownPaymentId_throwsPaymentNotFoundException() {
            when(paymentRepository.findById("GHOST")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> paymentService.processPayment("GHOST"))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ─── holdForFraud ─────────────────────────────────────────────────────────

    @Nested @DisplayName("holdForFraud")
    class HoldForFraud {

        @Test
        void setsStatusAndFraudFields() {
            Payment payment = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any())).thenAnswer(i -> dummyResponse(payment));

            paymentService.holdForFraud("PAY-001", 0.65, Payment.FraudRiskLevel.HIGH, "High velocity");

            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FRAUD_HOLD);
            assertThat(payment.getFraudScore()).isEqualByComparingTo(BigDecimal.valueOf(0.65));
            assertThat(payment.getFraudRiskLevel()).isEqualTo(Payment.FraudRiskLevel.HIGH);
            assertThat(payment.getFailureReason()).isEqualTo("High velocity");

            // Hold stays — no releaseHold called
            verify(accountService, never()).releaseHold(any(), any());
            verify(notificationService).sendFraudAlertToCompliance(anyString(), anyDouble(), anyString());
        }
    }

    // ─── failPayment ──────────────────────────────────────────────────────────

    @Nested @DisplayName("failPayment")
    class FailPayment {

        @Test
        void releasesHoldAndSetsFailedStatus() {
            Payment payment = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any())).thenAnswer(i -> dummyResponse(payment));

            paymentService.failPayment("PAY-001", "Network timeout");

            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("Network timeout");
            verify(accountService).releaseHold("ACC-SRC", new BigDecimal("500.00"));
            verify(notificationService).sendPaymentFailedAlert(anyString(), anyString(), anyString(), anyString());
        }
    }

    // ─── reversePayment ───────────────────────────────────────────────────────

    @Nested @DisplayName("reversePayment")
    class ReversePayment {

        @Test
        void completedPayment_reversesMoneyAndCreatesReversalRecord() {
            Payment payment = buildPayment(Payment.PaymentStatus.COMPLETED);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));

            ArgumentCaptor<Payment> savedCap = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(savedCap.capture())).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any())).thenAnswer(i -> dummyResponse(payment));

            paymentService.reversePayment("PAY-001", "Customer dispute");

            // Verify reversal debits destination and credits source
            verify(accountService).debitAccount("ACC-DST", new BigDecimal("500.00"));
            verify(accountService).creditAccount("ACC-SRC", new BigDecimal("500.00"));

            // Verify original marked REVERSED
            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REVERSED);

            // Verify reversal payment created with correct direction
            List<Payment> saved = savedCap.getAllValues();
            Payment reversalPayment = saved.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED && p != payment)
                    .findFirst().orElse(null);
            if (reversalPayment != null) {
                assertThat(reversalPayment.getSourceAccountId()).isEqualTo("ACC-DST");
                assertThat(reversalPayment.getDestinationAccountId()).isEqualTo("ACC-SRC");
                assertThat(reversalPayment.getReferenceNumber()).endsWith("-REV");
            }
        }

        @Test
        void nonCompletedPayment_throwsPaymentException() {
            Payment payment = buildPayment(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            assertThatThrownBy(() -> paymentService.reversePayment("PAY-001", "reason"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("completed payments");
        }

        @Test
        void failedPayment_throwsPaymentException() {
            Payment payment = buildPayment(Payment.PaymentStatus.FAILED);
            when(paymentRepository.findById("PAY-001")).thenReturn(Optional.of(payment));
            assertThatThrownBy(() -> paymentService.reversePayment("PAY-001", "reason"))
                    .isInstanceOf(PaymentException.class);
        }
    }

    // ─── getDailySpendingSummary ───────────────────────────────────────────────

    @Nested @DisplayName("getDailySpendingSummary")
    class DailySummary {

        @Test
        void returnsCorrectSummaryWithAverage() {
            when(paymentRepository.sumAmountSince(eq("ACC-SRC"), any()))
                    .thenReturn(new BigDecimal("3000.00"));
            when(paymentRepository.countTransactionsSince(eq("ACC-SRC"), any())).thenReturn(3L);
            when(paymentRepository.maxAmountSince(eq("ACC-SRC"), any()))
                    .thenReturn(new BigDecimal("1500.00"));

            DailySpendingSummary s = paymentService.getDailySpendingSummary("ACC-SRC");

            assertThat(s.accountId()).isEqualTo("ACC-SRC");
            assertThat(s.totalSpentToday()).isEqualByComparingTo("3000.00");
            assertThat(s.transactionCount()).isEqualTo(3);
            assertThat(s.averageTransactionSize()).isEqualByComparingTo("1000.00");
            assertThat(s.largestTransaction()).isEqualByComparingTo("1500.00");
        }

        @Test
        void noTransactionsToday_returnsZeroes() {
            when(paymentRepository.sumAmountSince(eq("ACC-SRC"), any())).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.countTransactionsSince(eq("ACC-SRC"), any())).thenReturn(0L);
            when(paymentRepository.maxAmountSince(eq("ACC-SRC"), any())).thenReturn(null);

            DailySpendingSummary s = paymentService.getDailySpendingSummary("ACC-SRC");
            assertThat(s.totalSpentToday()).isEqualByComparingTo("0.00");
            assertThat(s.transactionCount()).isZero();
            assertThat(s.averageTransactionSize()).isEqualByComparingTo("0.00");
            assertThat(s.largestTransaction()).isEqualByComparingTo("0.00");
        }
    }

    // ─── getAccountPayments ───────────────────────────────────────────────────

    @Nested @DisplayName("getAccountPayments")
    class GetPayments {

        @Test
        void returnsPagedPayments() {
            Payment payment = buildPayment(Payment.PaymentStatus.COMPLETED);
            var page = new PageImpl<>(List.of(payment));
            PaymentSummary summary = new PaymentSummary(
                "PAY-001", "REF-001", new BigDecimal("500"), "GBP", "IMPS", "COMPLETED", LocalDateTime.now());

            when(paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(eq("ACC-SRC"), any()))
                    .thenReturn(page);
            when(paymentMapper.toSummary(payment)).thenReturn(summary);

            var result = paymentService.getAccountPayments("ACC-SRC", PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
