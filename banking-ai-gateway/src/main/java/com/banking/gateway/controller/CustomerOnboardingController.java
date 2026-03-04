package com.banking.gateway.controller;

import com.banking.common.domain.ApiResponse;
import com.banking.common.domain.PagedResponse;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for customer onboarding lifecycle:
 * Initiate → Submit Docs → KYC Review → Complete → Account Open
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class CustomerOnboardingController {

    private final CustomerOnboardingService onboardingService;

    /**
     * POST /api/v1/onboarding/customers
     * Begin the onboarding journey for a new customer.
     */
    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<OnboardingResponse>> initiateOnboarding(
            @Valid @RequestBody OnboardingRequest request) {
        OnboardingResponse response = onboardingService.initiateOnboarding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Onboarding initiated", response));
    }

    /**
     * GET /api/v1/onboarding/customers/{customerId}
     */
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable String customerId) {
        return ResponseEntity.ok(ApiResponse.success(onboardingService.getCustomer(customerId)));
    }

    /**
     * PATCH /api/v1/onboarding/customers/{customerId}/kyc
     * Update KYC status after document review (for compliance officers).
     */
    @PatchMapping("/customers/{customerId}/kyc")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateKycStatus(
            @PathVariable String customerId,
            @Valid @RequestBody KycUpdateRequest request) {
        KycUpdateRequest withId = new KycUpdateRequest(customerId, request.kycStatus(), request.rejectionReason());
        return ResponseEntity.ok(ApiResponse.success("KYC status updated", onboardingService.updateKycStatus(withId)));
    }

    /**
     * POST /api/v1/onboarding/customers/{customerId}/complete
     * Finalise onboarding after successful KYC.
     */
    @PostMapping("/customers/{customerId}/complete")
    public ResponseEntity<ApiResponse<CustomerResponse>> completeOnboarding(
            @PathVariable String customerId) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding completed", onboardingService.completeOnboarding(customerId)));
    }

    /**
     * GET /api/v1/onboarding/customers?status=KYC_VERIFIED&page=0&size=20
     */
    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerSummary>>> getCustomersByStatus(
            @RequestParam(defaultValue = "INITIATED") String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Customer.OnboardingStatus onboardingStatus = Customer.OnboardingStatus.valueOf(status.toUpperCase());
        var results = onboardingService.getCustomersByStatus(
            onboardingStatus, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(results)));
    }

    /**
     * GET /api/v1/onboarding/kyc/pending
     * Returns all customers with KYC pending or under review (compliance queue).
     */
    @GetMapping("/kyc/pending")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerSummary>>> getPendingKyc(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        var results = onboardingService.getPendingKycCustomers(
            PageRequest.of(page, size, Sort.by("createdAt").ascending()));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(results)));
    }
}
