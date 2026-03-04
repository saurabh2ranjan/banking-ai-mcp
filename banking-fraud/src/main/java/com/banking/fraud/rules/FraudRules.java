package com.banking.fraud.rules;

import com.banking.payment.domain.Payment;
import com.banking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Each class implements one fraud rule.
 * Follows the Chain of Responsibility / Strategy pattern —
 * easy to add new rules without touching existing code (Open/Closed principle).
 */
public class FraudRules {

    public interface FraudRule {
        FraudRuleResult evaluate(Payment payment, PaymentService paymentService);
    }

    public record FraudRuleResult(String ruleName, boolean triggered, double scoreContribution, String description) {}

    // ─── Rule 1: High Value ────────────────────────────────────────────────

    @Component
    public static class HighValueRule implements FraudRule {
        private static final BigDecimal CRITICAL = new BigDecimal("100000");
        private static final BigDecimal HIGH     = new BigDecimal("50000");
        private static final BigDecimal MEDIUM   = new BigDecimal("10000");

        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            BigDecimal amt = p.getAmount();
            if (amt.compareTo(CRITICAL) > 0) return result(true, 0.45, "Critical value transaction: " + amt);
            if (amt.compareTo(HIGH)     > 0) return result(true, 0.30, "High value transaction: " + amt);
            if (amt.compareTo(MEDIUM)   > 0) return result(true, 0.15, "Large value transaction: " + amt);
            return result(false, 0.0, "Normal value");
        }

        private FraudRuleResult result(boolean t, double s, String d) { return new FraudRuleResult("HIGH_VALUE", t, s, d); }
    }

    // ─── Rule 2: Velocity Check ────────────────────────────────────────────

    @Component
    @RequiredArgsConstructor
    public static class VelocityRule implements FraudRule {
        private static final int CRITICAL_COUNT = 10;
        private static final int HIGH_COUNT     = 5;

        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            List<Payment> recent = ps.getRecentPayments(p.getSourceAccountId(), 1);
            int count = recent.size();
            if (count >= CRITICAL_COUNT) return new FraudRuleResult("VELOCITY", true, 0.40, count + " payments in 1h (critical velocity)");
            if (count >= HIGH_COUNT)     return new FraudRuleResult("VELOCITY", true, 0.25, count + " payments in 1h (high velocity)");
            return new FraudRuleResult("VELOCITY", false, 0.0, "Normal velocity");
        }
    }

    // ─── Rule 3: Off-Hours ─────────────────────────────────────────────────

    @Component
    public static class OffHoursRule implements FraudRule {
        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            int hour = LocalDateTime.now().getHour();
            boolean offHours = hour >= 23 || hour <= 5;
            return new FraudRuleResult("OFF_HOURS", offHours, offHours ? 0.10 : 0.0,
                offHours ? "Transaction initiated at unusual hour: " + hour + ":00" : "Normal hours");
        }
    }

    // ─── Rule 4: SWIFT/International ──────────────────────────────────────

    @Component
    public static class InternationalWireRule implements FraudRule {
        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            boolean swift = Payment.PaymentType.SWIFT.equals(p.getPaymentType());
            return new FraudRuleResult("INTERNATIONAL_WIRE", swift, swift ? 0.15 : 0.0,
                swift ? "International SWIFT transfer — inherently higher risk" : "Domestic transfer");
        }
    }

    // ─── Rule 5: Round Amount ─────────────────────────────────────────────

    @Component
    public static class RoundAmountRule implements FraudRule {
        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            boolean round = p.getAmount().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0;
            return new FraudRuleResult("ROUND_AMOUNT", round, round ? 0.08 : 0.0,
                round ? "Suspiciously round amount: " + p.getAmount() : "Non-round amount");
        }
    }

    // ─── Rule 6: Daily Limit ───────────────────────────────────────────────

    @Component
    @RequiredArgsConstructor
    public static class DailyLimitRule implements FraudRule {
        private static final BigDecimal DAILY_ALERT_THRESHOLD = new BigDecimal("200000");

        @Override
        public FraudRuleResult evaluate(Payment p, PaymentService ps) {
            var summary = ps.getDailySpendingSummary(p.getSourceAccountId());
            BigDecimal projectedTotal = summary.totalSpentToday().add(p.getAmount());
            boolean exceeded = projectedTotal.compareTo(DAILY_ALERT_THRESHOLD) > 0;
            return new FraudRuleResult("DAILY_LIMIT", exceeded, exceeded ? 0.25 : 0.0,
                exceeded ? "Projected daily total " + projectedTotal + " exceeds alert threshold" : "Within daily limits");
        }
    }
}
