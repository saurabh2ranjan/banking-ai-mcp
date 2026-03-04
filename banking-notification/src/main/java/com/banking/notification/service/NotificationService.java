package com.banking.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Notification service for all banking events.
 *
 * Production implementation would:
 * - Use Spring Mail for email (with HTML templates via Thymeleaf)
 * - Use Twilio/AWS SNS for SMS
 * - Use Firebase/APNs for push notifications
 * - Publish to a Kafka topic for event-driven async processing
 *
 * For this demo, all notifications are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // ─── Onboarding ──────────────────────────────────────────────────────

    public void sendWelcomeEmail(String email, String fullName) {
        log.info("📧 [EMAIL] Welcome email → {} ({})", email, fullName);
    }

    public void sendKycApprovedEmail(String email, String fullName) {
        log.info("📧 [EMAIL] KYC approved → {} ({})", email, fullName);
    }

    public void sendKycRejectedEmail(String email, String fullName, String reason) {
        log.warn("📧 [EMAIL] KYC rejected → {} ({}) | Reason: {}", email, fullName, reason);
    }

    public void sendOnboardingCompleteEmail(String email, String fullName) {
        log.info("📧 [EMAIL] Onboarding complete → {} ({})", email, fullName);
    }

    // ─── Account ─────────────────────────────────────────────────────────

    public void sendAccountOpenedNotification(String email, String fullName, String accountId, String accountType) {
        log.info("📧 [EMAIL] Account opened → {} | AccountID: {} | Type: {}", fullName, accountId, accountType);
    }

    public void sendAccountBlockedAlert(String email, String fullName, String accountId, String reason) {
        log.warn("🚨 [EMAIL+SMS] Account BLOCKED → {} | AccountID: {} | Reason: {}", fullName, accountId, reason);
    }

    // ─── Payment ─────────────────────────────────────────────────────────

    public void sendPaymentInitiatedSms(String mobile, String paymentId, BigDecimal amount, String currency) {
        log.info("📱 [SMS] Payment initiated → {} | PaymentID: {} | Amount: {} {}", mobile, paymentId, amount, currency);
    }

    public void sendPaymentCompletedEmail(String email, String fullName,
                                          String paymentId, BigDecimal amount,
                                          String currency, String reference) {
        log.info("📧 [EMAIL] Payment completed → {} | Ref: {} | Amount: {} {}", fullName, reference, amount, currency);
    }

    public void sendPaymentFailedAlert(String email, String fullName, String paymentId, String reason) {
        log.warn("📧 [EMAIL] Payment FAILED → {} | PaymentID: {} | Reason: {}", fullName, paymentId, reason);
    }

    // ─── Fraud ───────────────────────────────────────────────────────────

    public void sendFraudAlertToCompliance(String paymentId, double fraudScore, String riskLevel) {
        log.warn("🚨 [COMPLIANCE-ALERT] Fraud detected | PaymentID: {} | Score: {} | Level: {}",
                 paymentId, fraudScore, riskLevel);
    }

    public void sendFraudHoldSmsToCustomer(String mobile, String paymentId) {
        log.warn("📱 [SMS] Payment on fraud hold → {} | PaymentID: {}", mobile, paymentId);
    }

    public void sendAccountFraudAlertToCustomer(String email, String mobile, String accountId) {
        log.warn("🚨 [EMAIL+SMS] Account fraud alert → email: {} | AccountID: {}", email, accountId);
    }
}
