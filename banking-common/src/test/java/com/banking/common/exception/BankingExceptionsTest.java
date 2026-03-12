package com.banking.common.exception;

import com.banking.common.exception.BankingExceptions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BankingExceptionsTest {

    @Test
    void customerNotFoundException_hasCorrectMessageAndCodes() {
        var ex = new CustomerNotFoundException("CUST-001");
        assertThat(ex.getMessage()).contains("Customer").contains("CUST-001");
        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void accountNotFoundException_hasCorrectMessageAndCodes() {
        var ex = new AccountNotFoundException("ACC-001");
        assertThat(ex.getMessage()).contains("Account").contains("ACC-001");
        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void paymentNotFoundException_hasCorrectMessageAndCodes() {
        var ex = new PaymentNotFoundException("PAY-001");
        assertThat(ex.getMessage()).contains("Payment").contains("PAY-001");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void insufficientFundsException_hasCorrectErrorCodeAndStatus() {
        var ex = new InsufficientFundsException("ACC-001");
        assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getMessage()).contains("ACC-001");
    }

    @Test
    void duplicateResourceException_hasCorrectErrorCodeAndStatus() {
        var ex = new DuplicateResourceException("Customer", "email: a@b.com");
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("Customer").contains("email: a@b.com");
    }

    @Test
    void accountInactiveException_hasCorrectErrorCodeAndStatus() {
        var ex = new AccountInactiveException("ACC-BLK");
        assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_INACTIVE");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void dailyLimitExceededException_hasCorrectErrorCodeAndStatus() {
        var ex = new DailyLimitExceededException("ACC-001", "10000");
        assertThat(ex.getErrorCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getMessage()).contains("ACC-001").contains("10000");
    }

    @Test
    void paymentException_defaultCode_isPaymentFailed() {
        var ex = new PaymentException("Payment timed out");
        assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void paymentException_customErrorCode_isPreserved() {
        var ex = new PaymentException("Limit", "SINGLE_TXN_LIMIT_EXCEEDED");
        assertThat(ex.getErrorCode()).isEqualTo("SINGLE_TXN_LIMIT_EXCEEDED");
    }

    @Test
    void paymentFraudHoldException_hasCorrectErrorCodeAndStatus() {
        var ex = new PaymentFraudHoldException("PAY-001");
        assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_FRAUD_HOLD");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void kycFailedException_hasCorrectErrorCodeAndStatus() {
        var ex = new KycFailedException("PAN invalid");
        assertThat(ex.getErrorCode()).isEqualTo("KYC_FAILED");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getMessage()).contains("PAN invalid");
    }

    @Test
    void onboardingException_hasCorrectErrorCodeAndStatus() {
        var ex = new OnboardingException("KYC not verified");
        assertThat(ex.getErrorCode()).isEqualTo("ONBOARDING_FAILED");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void concurrentModificationException_hasCorrectErrorCodeAndStatus() {
        var ex = new ConcurrentModificationException("Account");
        assertThat(ex.getErrorCode()).isEqualTo("OPTIMISTIC_LOCK_FAILURE");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void allExceptions_extendBankingExceptionAndRuntimeException() {
        assertThat(new CustomerNotFoundException("x")).isInstanceOf(BankingException.class);
        assertThat(new AccountNotFoundException("x")).isInstanceOf(BankingException.class);
        assertThat(new InsufficientFundsException("x")).isInstanceOf(BankingException.class);
        assertThat(new KycFailedException("x")).isInstanceOf(RuntimeException.class);
        assertThat(new OnboardingException("x")).isInstanceOf(RuntimeException.class);
    }
}
