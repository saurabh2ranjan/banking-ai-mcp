package com.banking.payment.service;

import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.notification.service.NotificationService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.mapper.PaymentMapper;
import com.banking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository   paymentRepository;
    private final PaymentMapper       paymentMapper;
    private final AccountService      accountService;
    private final NotificationService notificationService;

    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest req) {
        log.info("Initiating {} payment: {} → {} | {} {}",
                 req.paymentType(), req.sourceAccountId(), req.destinationAccountId(),
                 req.amount(), req.currency());

        // Account validation
        if (!accountService.validateAccountActive(req.sourceAccountId()))
            throw new AccountInactiveException(req.sourceAccountId());
        if (!accountService.validateAccountActive(req.destinationAccountId()))
            throw new AccountInactiveException(req.destinationAccountId());

        // Funds check
        if (!accountService.hasSufficientFunds(req.sourceAccountId(), req.amount()))
            throw new InsufficientFundsException(req.sourceAccountId());

        // Place a hold immediately so funds can't be double-spent
        accountService.placeHold(req.sourceAccountId(), req.amount());

        Payment payment = Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .referenceNumber(generateReference(req.paymentType()))
            .customerId(req.customerId())
            .sourceAccountId(req.sourceAccountId())
            .destinationAccountId(req.destinationAccountId())
            .amount(req.amount())
            .currency(req.currency())
            .paymentType(req.paymentType())
            .status(Payment.PaymentStatus.PENDING_FRAUD_CHECK)
            .description(req.description())
            .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: {} ({})", saved.getPaymentId(), saved.getReferenceNumber());

        notificationService.sendPaymentInitiatedSms(
            req.sourceAccountId(), saved.getPaymentId(), req.amount(), req.currency());

        return paymentMapper.toResponse(saved);
    }

    @Transactional
    public PaymentResponse processPayment(String paymentId) {
        Payment payment = findPayment(paymentId);

        if (!Payment.PaymentStatus.PENDING_FRAUD_CHECK.equals(payment.getStatus()))
            throw new PaymentException("Payment cannot be processed from state: " + payment.getStatus());

        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        // Release hold and execute real transfer
        accountService.releaseHold(payment.getSourceAccountId(), payment.getAmount());
        accountService.debitAccount(payment.getSourceAccountId(), payment.getAmount());
        accountService.creditAccount(payment.getDestinationAccountId(), payment.getAmount());

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        notificationService.sendPaymentCompletedEmail(
            payment.getSourceAccountId(), "Customer", payment.getPaymentId(),
            payment.getAmount(), payment.getCurrency(), payment.getReferenceNumber());

        log.info("Payment COMPLETED: {} ({})", paymentId, payment.getReferenceNumber());
        return paymentMapper.toResponse(saved);
    }

    @Transactional
    public PaymentResponse holdForFraud(String paymentId, double fraudScore,
                                        Payment.FraudRiskLevel riskLevel, String reason) {
        Payment payment = findPayment(paymentId);
        payment.setStatus(Payment.PaymentStatus.FRAUD_HOLD);
        payment.setFraudScore(java.math.BigDecimal.valueOf(fraudScore));
        payment.setFraudRiskLevel(riskLevel);
        payment.setFailureReason(reason);
        // Hold stays in place — amount not released
        notificationService.sendFraudAlertToCompliance(paymentId, fraudScore, riskLevel.name());
        log.warn("🚨 Payment on FRAUD_HOLD: {} | Score: {} | Level: {}", paymentId, fraudScore, riskLevel);
        return paymentMapper.toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse failPayment(String paymentId, String reason) {
        Payment payment = findPayment(paymentId);
        // Release hold back to customer
        accountService.releaseHold(payment.getSourceAccountId(), payment.getAmount());
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        notificationService.sendPaymentFailedAlert(
            payment.getSourceAccountId(), "Customer", paymentId, reason);
        return paymentMapper.toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse reversePayment(String paymentId, String reason) {
        Payment original = findPayment(paymentId);
        if (!Payment.PaymentStatus.COMPLETED.equals(original.getStatus()))
            throw new PaymentException("Only completed payments can be reversed");

        // Reverse the money flow
        accountService.debitAccount(original.getDestinationAccountId(), original.getAmount());
        accountService.creditAccount(original.getSourceAccountId(), original.getAmount());

        Payment reversal = Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .referenceNumber(generateReference(original.getPaymentType()) + "-REV")
            .customerId(original.getCustomerId())
            .sourceAccountId(original.getDestinationAccountId())
            .destinationAccountId(original.getSourceAccountId())
            .amount(original.getAmount())
            .currency(original.getCurrency())
            .paymentType(original.getPaymentType())
            .status(Payment.PaymentStatus.COMPLETED)
            .description("Reversal of " + original.getReferenceNumber() + ": " + reason)
            .completedAt(LocalDateTime.now())
            .build();

        original.setStatus(Payment.PaymentStatus.REVERSED);
        original.setReversalPaymentId(reversal.getPaymentId());
        paymentRepository.save(original);

        log.info("Payment REVERSED: {} | Reversal: {}", paymentId, reversal.getPaymentId());
        return paymentMapper.toResponse(paymentRepository.save(reversal));
    }

    // ─── Queries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        return paymentMapper.toResponse(findPayment(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentSummary> getAccountPayments(String accountId, Pageable pageable) {
        return paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(accountId, pageable)
                                .map(paymentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public List<Payment> getRecentPayments(String accountId, int hours) {
        return paymentRepository.findRecentBySourceAccount(
            accountId, LocalDateTime.now().minusHours(hours));
    }

    @Transactional(readOnly = true)
    public DailySpendingSummary getDailySpendingSummary(String accountId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        BigDecimal total   = paymentRepository.sumAmountSince(accountId, startOfDay);
        long count         = paymentRepository.countTransactionsSince(accountId, startOfDay);
        BigDecimal largest = paymentRepository.maxAmountSince(accountId, startOfDay);
        BigDecimal avg     = count > 0 ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new DailySpendingSummary(accountId, total, (int) count, avg,
                                        largest != null ? largest : BigDecimal.ZERO, "USD");
    }

    // ─── Internal ─────────────────────────────────────────────────────────

    public Payment findPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private String generateReference(Payment.PaymentType type) {
        return type.name() + "-" + System.currentTimeMillis() + "-"
             + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
