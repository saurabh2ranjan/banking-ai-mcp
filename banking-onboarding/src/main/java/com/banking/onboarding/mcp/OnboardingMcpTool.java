package com.banking.onboarding.mcp;

import com.banking.common.exception.BankingExceptions.BankingException;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Tools for Customer Onboarding.
 * Exposes customer lookup, KYC status management, and onboarding pipeline
 * queries to the AI model for conversational banking operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingMcpTool {

    private final CustomerOnboardingService onboardingService;

    @Tool(
        name = "get_customer_profile",
        description = "Retrieve complete customer profile including personal details, KYC status, " +
                      "onboarding status, address, employment, and risk classification by customer ID."
    )
    public Map<String, Object> getCustomerProfile(
            @ToolParam(description = "Customer ID (e.g. CUST-00012345)") String customerId) {
        try {
            CustomerResponse c = onboardingService.getCustomer(customerId);
            return Map.ofEntries(
                Map.entry("customerId",       c.customerId()),
                Map.entry("fullName",         c.fullName()),
                Map.entry("email",            c.email()),
                Map.entry("mobile",           c.mobile()),
                Map.entry("dateOfBirth",      c.dateOfBirth().toString()),
                Map.entry("nationality",      c.nationality() != null ? c.nationality() : "N/A"),
                Map.entry("panNumber",        c.panNumber()   != null ? c.panNumber()   : "N/A"),
                Map.entry("kycStatus",        c.kycStatus()),
                Map.entry("onboardingStatus", c.onboardingStatus()),
                Map.entry("riskCategory",     c.riskCategory()),
                Map.entry("employmentType",   c.employmentType() != null ? c.employmentType() : "N/A"),
                Map.entry("annualIncome",     c.annualIncome()   != null ? c.annualIncome().toString() : "N/A"),
                Map.entry("address",          c.address()        != null ? c.address().formatted() : "N/A"),
                Map.entry("createdAt",        c.createdAt().toString())
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(
        name = "get_customer_by_email",
        description = "Look up a customer profile using their registered email address."
    )
    public Map<String, Object> getCustomerByEmail(
            @ToolParam(description = "Customer's email address") String email) {
        try {
            CustomerResponse c = onboardingService.getCustomerByEmail(email);
            return Map.of(
                "customerId",       c.customerId(),
                "fullName",         c.fullName(),
                "email",            c.email(),
                "kycStatus",        c.kycStatus(),
                "onboardingStatus", c.onboardingStatus(),
                "riskCategory",     c.riskCategory()
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(
        name = "update_kyc_status",
        description = "Update the KYC (Know Your Customer) verification status for a customer. " +
                      "Valid statuses: VERIFIED, REJECTED, UNDER_REVIEW. " +
                      "Rejection requires a reason. Approval triggers account creation eligibility."
    )
    public Map<String, Object> updateKycStatus(
            @ToolParam(description = "Customer ID to update") String customerId,
            @ToolParam(description = "New KYC status: VERIFIED, REJECTED, or UNDER_REVIEW") String kycStatus,
            @ToolParam(description = "Reason if rejecting (required for REJECTED status)") String reason) {
        try {
            Customer.KycStatus status = Customer.KycStatus.valueOf(kycStatus.toUpperCase());
            KycUpdateRequest request = new KycUpdateRequest(customerId, status, reason);
            CustomerResponse updated = onboardingService.updateKycStatus(request);
            return Map.of(
                "customerId",       updated.customerId(),
                "fullName",         updated.fullName(),
                "kycStatus",        updated.kycStatus(),
                "onboardingStatus", updated.onboardingStatus(),
                "message",          "KYC status updated to " + kycStatus
            );
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid KYC status. Valid values: VERIFIED, REJECTED, UNDER_REVIEW, PENDING");
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(
        name = "get_pending_kyc_customers",
        description = "List customers whose KYC is pending or under review. " +
                      "Used by compliance officers to action the KYC review queue."
    )
    public Map<String, Object> getPendingKycCustomers(
            @ToolParam(description = "Page number (0-indexed)") int page,
            @ToolParam(description = "Page size (max 50)") int size) {
        Page<CustomerSummary> results = onboardingService.getPendingKycCustomers(
            PageRequest.of(page, Math.min(size, 50)));
        return Map.of(
            "totalPending",   results.getTotalElements(),
            "page",           page,
            "totalPages",     results.getTotalPages(),
            "customers",      results.getContent().stream()
                .map(c -> Map.of(
                    "customerId",       c.customerId(),
                    "fullName",         c.fullName(),
                    "email",            c.email(),
                    "kycStatus",        c.kycStatus(),
                    "onboardingStatus", c.onboardingStatus(),
                    "createdAt",        c.createdAt().toString()
                ))
                .collect(Collectors.toList())
        );
    }

    @Tool(
        name = "complete_customer_onboarding",
        description = "Mark a customer's onboarding as COMPLETED after KYC has been verified. " +
                      "This unlocks account creation for the customer."
    )
    public Map<String, Object> completeOnboarding(
            @ToolParam(description = "Customer ID to complete onboarding for") String customerId) {
        try {
            CustomerResponse updated = onboardingService.completeOnboarding(customerId);
            return Map.of(
                "customerId",       updated.customerId(),
                "fullName",         updated.fullName(),
                "onboardingStatus", updated.onboardingStatus(),
                "message",          "Onboarding completed. Customer is now eligible to open accounts."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }
}
