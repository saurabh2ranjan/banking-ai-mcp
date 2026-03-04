package com.banking.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception hierarchy for the banking platform.
 * All exceptions include an errorCode for client-facing responses
 * and a suggested HTTP status for the global exception handler.
 */
public class BankingExceptions {

    // ─── Base ────────────────────────────────────────────────────────────────

    public static class BankingException extends RuntimeException {
        private final String     errorCode;
        private final HttpStatus httpStatus;

        public BankingException(String message, String errorCode, HttpStatus httpStatus) {
            super(message);
            this.errorCode  = errorCode;
            this.httpStatus = httpStatus;
        }

        public String     getErrorCode()  { return errorCode;  }
        public HttpStatus getHttpStatus() { return httpStatus; }
    }

    // ─── Resource not found ──────────────────────────────────────────────────

    public static class ResourceNotFoundException extends BankingException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found: " + id, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
    }

    public static class CustomerNotFoundException extends ResourceNotFoundException {
        public CustomerNotFoundException(String id) { super("Customer", id); }
    }

    public static class AccountNotFoundException extends ResourceNotFoundException {
        public AccountNotFoundException(String id) { super("Account", id); }
    }

    public static class PaymentNotFoundException extends ResourceNotFoundException {
        public PaymentNotFoundException(String id) { super("Payment", id); }
    }

    // ─── Business rule violations ────────────────────────────────────────────

    public static class InsufficientFundsException extends BankingException {
        public InsufficientFundsException(String accountId) {
            super("Insufficient funds in account: " + accountId, "INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public static class DuplicateResourceException extends BankingException {
        public DuplicateResourceException(String resource, String field) {
            super(resource + " already exists with " + field, "DUPLICATE_RESOURCE", HttpStatus.CONFLICT);
        }
    }

    public static class AccountInactiveException extends BankingException {
        public AccountInactiveException(String accountId) {
            super("Account is not active: " + accountId, "ACCOUNT_INACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public static class DailyLimitExceededException extends BankingException {
        public DailyLimitExceededException(String accountId, String limit) {
            super("Daily transfer limit exceeded for account " + accountId + ". Limit: " + limit, "DAILY_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // ─── Payment specific ────────────────────────────────────────────────────

    public static class PaymentException extends BankingException {
        public PaymentException(String message) {
            super(message, "PAYMENT_FAILED", HttpStatus.BAD_REQUEST);
        }
        public PaymentException(String message, String errorCode) {
            super(message, errorCode, HttpStatus.BAD_REQUEST);
        }
    }

    public static class PaymentFraudHoldException extends BankingException {
        public PaymentFraudHoldException(String paymentId) {
            super("Payment is on fraud hold and cannot be processed: " + paymentId, "PAYMENT_FRAUD_HOLD", HttpStatus.FORBIDDEN);
        }
    }

    // ─── Onboarding ──────────────────────────────────────────────────────────

    public static class KycFailedException extends BankingException {
        public KycFailedException(String reason) {
            super("KYC verification failed: " + reason, "KYC_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public static class OnboardingException extends BankingException {
        public OnboardingException(String message) {
            super(message, "ONBOARDING_FAILED", HttpStatus.BAD_REQUEST);
        }
    }

    // ─── System ──────────────────────────────────────────────────────────────

    public static class ConcurrentModificationException extends BankingException {
        public ConcurrentModificationException(String resource) {
            super(resource + " was modified by another transaction. Please retry.", "OPTIMISTIC_LOCK_FAILURE", HttpStatus.CONFLICT);
        }
    }
}
