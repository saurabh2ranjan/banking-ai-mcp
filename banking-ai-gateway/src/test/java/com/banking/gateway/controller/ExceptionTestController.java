package com.banking.gateway.controller;

import com.banking.common.exception.BankingExceptions.AccountNotFoundException;
import com.banking.common.exception.BankingExceptions.CustomerNotFoundException;
import com.banking.common.exception.BankingExceptions.DuplicateResourceException;
import com.banking.common.exception.BankingExceptions.InsufficientFundsException;
import com.banking.common.exception.BankingExceptions.KycFailedException;
import com.banking.common.exception.BankingExceptions.OnboardingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal controller used ONLY in GlobalExceptionHandlerTest.
 * Each endpoint throws a specific exception to verify handler mappings.
 * NOT a real banking controller — test infrastructure only.
 */
@RestController
public class ExceptionTestController {

    @GetMapping("/test/customer-not-found")
    public void customerNotFound() {
        throw new CustomerNotFoundException("CUST-001");
    }

    @GetMapping("/test/account-not-found")
    public void accountNotFound() {
        throw new AccountNotFoundException("ACC-001");
    }

    @GetMapping("/test/duplicate-resource")
    public void duplicateResource() {
        throw new DuplicateResourceException("Customer", "email: alice@example.com");
    }

    @GetMapping("/test/kyc-failed")
    public void kycFailed() {
        throw new KycFailedException("Under 18 years old");
    }

    @GetMapping("/test/onboarding-failed")
    public void onboardingFailed() {
        throw new OnboardingException("KYC not verified");
    }

    @GetMapping("/test/insufficient-funds")
    public void insufficientFunds() {
        throw new InsufficientFundsException("ACC-001");
    }

    @GetMapping("/test/optimistic-lock")
    public void optimisticLock() {
        throw new OptimisticLockingFailureException("Concurrent modification");
    }

    @GetMapping("/test/illegal-argument")
    public void illegalArgument() {
        throw new IllegalArgumentException("Invalid value provided");
    }

    @GetMapping("/test/unexpected")
    public void unexpected() {
        throw new RuntimeException("Something went terribly wrong");
    }

    @PostMapping("/test/missing-body")
    public void missingBody(@RequestBody String body) {
        // triggers HttpMessageNotReadableException when body is absent
    }

    @PostMapping("/test/validation")
    public void validation(@Valid @RequestBody ValidationRequest request) {
        // triggers MethodArgumentNotValidException when name is blank
    }

    public record ValidationRequest(@NotBlank(message = "name must not be blank") String name) {}
}