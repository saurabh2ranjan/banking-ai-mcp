package com.banking.onboarding.mcp;

import com.banking.common.exception.BankingExceptions.CustomerNotFoundException;
import com.banking.common.exception.BankingExceptions.OnboardingException;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingMcpToolTest {

    @Mock        CustomerOnboardingService onboardingService;
    @InjectMocks OnboardingMcpTool         onboardingMcpTool;

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerResponse customerResp(String id, String kyc, String onboarding) {
        return new CustomerResponse(
            id, "Alice", "Johnson", "Alice Johnson",
            LocalDate.of(1990, 5, 15), "FEMALE",
            "alice@example.com", "+447700900001", "British",
            "ABCDE1234F", "PAN_CARD", LocalDate.of(2030, 12, 31),
            kyc, onboarding, "LOW", null,
            "SALARIED", "Tech Corp", new BigDecimal("80000"), "GBP",
            LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private CustomerSummary customerSummary(String id, String kyc, String onboarding) {
        return new CustomerSummary(id, "Alice Johnson", "alice@example.com",
                "+447700900001", kyc, onboarding, LocalDateTime.now());
    }

    // ── get_customer_profile ──────────────────────────────────────────────────

    @Test
    void getCustomerProfile_existingCustomer_returnsFullProfile() {
        when(onboardingService.getCustomer("CUST-001"))
                .thenReturn(customerResp("CUST-001", "VERIFIED", "COMPLETED"));

        Map<String, Object> result = onboardingMcpTool.getCustomerProfile("CUST-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("fullName")).isEqualTo("Alice Johnson");
        assertThat(result.get("email")).isEqualTo("alice@example.com");
        assertThat(result.get("kycStatus")).isEqualTo("VERIFIED");
        assertThat(result.get("onboardingStatus")).isEqualTo("COMPLETED");
        assertThat(result.get("riskCategory")).isEqualTo("LOW");
        assertThat(result).containsKeys(
            "customerId", "fullName", "email", "mobile", "dateOfBirth",
            "nationality", "panNumber", "kycStatus", "onboardingStatus",
            "riskCategory", "employmentType", "annualIncome", "address", "createdAt"
        );
    }

    @Test
    void getCustomerProfile_notFound_returnsErrorWithCode() {
        when(onboardingService.getCustomer("GHOST"))
                .thenThrow(new CustomerNotFoundException("GHOST"));

        Map<String, Object> result = onboardingMcpTool.getCustomerProfile("GHOST");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void getCustomerProfile_nullNationality_returnsNA() {
        CustomerResponse respWithNulls = new CustomerResponse(
            "CUST-002", "Bob", "Smith", "Bob Smith",
            LocalDate.of(1985, 3, 20), "MALE",
            "bob@example.com", "+447700900002",
            null,   // ← null nationality
            null,   // ← null panNumber
            null, null,
            "PENDING", "INITIATED", "LOW", null,
            null, null, null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );
        when(onboardingService.getCustomer("CUST-002")).thenReturn(respWithNulls);

        Map<String, Object> result = onboardingMcpTool.getCustomerProfile("CUST-002");

        assertThat(result.get("nationality")).isEqualTo("N/A");
        assertThat(result.get("panNumber")).isEqualTo("N/A");
        assertThat(result.get("employmentType")).isEqualTo("N/A");
        assertThat(result.get("annualIncome")).isEqualTo("N/A");
        assertThat(result.get("address")).isEqualTo("N/A");
    }

    // ── get_customer_by_email ─────────────────────────────────────────────────

    @Test
    void getCustomerByEmail_existingEmail_returnsCustomerSummary() {
        when(onboardingService.getCustomerByEmail("alice@example.com"))
                .thenReturn(customerResp("CUST-001", "VERIFIED", "COMPLETED"));

        Map<String, Object> result = onboardingMcpTool.getCustomerByEmail("alice@example.com");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("fullName")).isEqualTo("Alice Johnson");
        assertThat(result.get("email")).isEqualTo("alice@example.com");
        assertThat(result.get("kycStatus")).isEqualTo("VERIFIED");
        assertThat(result.get("onboardingStatus")).isEqualTo("COMPLETED");
        assertThat(result.get("riskCategory")).isEqualTo("LOW");
    }

    @Test
    void getCustomerByEmail_notFound_returnsError() {
        when(onboardingService.getCustomerByEmail("ghost@example.com"))
                .thenThrow(new CustomerNotFoundException("ghost@example.com"));

        Map<String, Object> result = onboardingMcpTool.getCustomerByEmail("ghost@example.com");

        assertThat(result).containsKey("error");
        // note: getCustomerByEmail only returns "error", not "errorCode"
        assertThat(result).doesNotContainKey("errorCode");
    }

    // ── update_kyc_status ─────────────────────────────────────────────────────

    @Test
    void updateKycStatus_verifiedStatus_returnsUpdatedCustomer() {
        when(onboardingService.updateKycStatus(any()))
                .thenReturn(customerResp("CUST-001", "VERIFIED", "KYC_VERIFIED"));

        Map<String, Object> result = onboardingMcpTool.updateKycStatus(
                "CUST-001", "VERIFIED", null);

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("kycStatus")).isEqualTo("VERIFIED");
        assertThat(result.get("message").toString()).contains("VERIFIED");
    }

    @Test
    void updateKycStatus_rejectedStatus_returnsUpdatedCustomer() {
        when(onboardingService.updateKycStatus(any()))
                .thenReturn(customerResp("CUST-001", "REJECTED", "REJECTED"));

        Map<String, Object> result = onboardingMcpTool.updateKycStatus(
                "CUST-001", "REJECTED", "PAN card mismatch");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("kycStatus")).isEqualTo("REJECTED");
        assertThat(result.get("message").toString()).contains("REJECTED");
    }

    @Test
    void updateKycStatus_invalidStatus_returnsValidationError() {
        Map<String, Object> result = onboardingMcpTool.updateKycStatus(
                "CUST-001", "INVALID_STATUS", null);

        assertThat(result).containsKey("error");
        assertThat(result.get("error").toString()).contains("Invalid KYC status");
        assertThat(result.get("error").toString()).contains("VERIFIED");
        assertThat(result.get("error").toString()).contains("REJECTED");
        // service should never be called for invalid status
        verifyNoInteractions(onboardingService);
    }

    @Test
    void updateKycStatus_bankingException_returnsErrorWithCode() {
        when(onboardingService.updateKycStatus(any()))
                .thenThrow(new CustomerNotFoundException("CUST-001"));

        Map<String, Object> result = onboardingMcpTool.updateKycStatus(
                "CUST-001", "VERIFIED", null);

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void updateKycStatus_lowercaseStatus_isAccepted() {
        when(onboardingService.updateKycStatus(any()))
                .thenReturn(customerResp("CUST-001", "VERIFIED", "KYC_VERIFIED"));

        // tool should uppercase the status before parsing
        Map<String, Object> result = onboardingMcpTool.updateKycStatus(
                "CUST-001", "verified", null);

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("kycStatus")).isEqualTo("VERIFIED");
    }

    // ── get_pending_kyc_customers ─────────────────────────────────────────────

    @Test
    void getPendingKycCustomers_returnsPaginatedResults() {
        List<CustomerSummary> summaries = List.of(
            customerSummary("CUST-001", "PENDING",      "INITIATED"),
            customerSummary("CUST-002", "UNDER_REVIEW", "DOCUMENTS_SUBMITTED")
        );
        when(onboardingService.getPendingKycCustomers(any()))
                .thenReturn(new PageImpl<>(summaries, PageRequest.of(0, 10), 2));

        Map<String, Object> result = onboardingMcpTool.getPendingKycCustomers(0, 10);

        assertThat(result.get("totalPending")).isEqualTo(2L);
        assertThat(result.get("page")).isEqualTo(0);
        assertThat(result.get("totalPages")).isEqualTo(1);
        List<?> customers = (List<?>) result.get("customers");
        assertThat(customers).hasSize(2);
    }

    @Test
    void getPendingKycCustomers_noPendingCustomers_returnsEmptyList() {
        when(onboardingService.getPendingKycCustomers(any()))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Object> result = onboardingMcpTool.getPendingKycCustomers(0, 10);

        assertThat(result.get("totalPending")).isEqualTo(0L);
        assertThat((List<?>) result.get("customers")).isEmpty();
    }

    @Test
    void getPendingKycCustomers_sizeExceeds50_isCappedAt50() {
        when(onboardingService.getPendingKycCustomers(any()))
                .thenReturn(new PageImpl<>(List.of()));

        onboardingMcpTool.getPendingKycCustomers(0, 100);

        // verify PageRequest was created with max 50, not 100
        verify(onboardingService).getPendingKycCustomers(PageRequest.of(0, 50));
    }

    @Test
    void getPendingKycCustomers_customerMapContainsExpectedFields() {
        when(onboardingService.getPendingKycCustomers(any()))
                .thenReturn(new PageImpl<>(List.of(
                    customerSummary("CUST-001", "PENDING", "INITIATED")
                )));

        Map<String, Object> result = onboardingMcpTool.getPendingKycCustomers(0, 10);

        List<?> customers = (List<?>) result.get("customers");
        Map<?, ?> first = (Map<?, ?>) customers.getFirst();
        assertThat(first.get("customerId")).isEqualTo("CUST-001");
        assertThat(first.get("fullName")).isNotNull();
        assertThat(first.get("email")).isNotNull();
        assertThat(first.get("kycStatus")).isEqualTo("PENDING");
        assertThat(first.get("onboardingStatus")).isEqualTo("INITIATED");
        assertThat(first.get("createdAt")).isNotNull();
    }

    // ── complete_customer_onboarding ──────────────────────────────────────────

    @Test
    void completeOnboarding_kycVerified_returnsCompletedStatus() {
        when(onboardingService.completeOnboarding("CUST-001"))
                .thenReturn(customerResp("CUST-001", "VERIFIED", "COMPLETED"));

        Map<String, Object> result = onboardingMcpTool.completeOnboarding("CUST-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("fullName")).isEqualTo("Alice Johnson");
        assertThat(result.get("onboardingStatus")).isEqualTo("COMPLETED");
        assertThat(result.get("message").toString()).contains("eligible to open accounts");
    }

    @Test
    void completeOnboarding_kycNotVerified_returnsError() {
        when(onboardingService.completeOnboarding("CUST-001"))
                .thenThrow(new OnboardingException("KYC is not verified"));

        Map<String, Object> result = onboardingMcpTool.completeOnboarding("CUST-001");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void completeOnboarding_customerNotFound_returnsError() {
        when(onboardingService.completeOnboarding("GHOST"))
                .thenThrow(new CustomerNotFoundException("GHOST"));

        Map<String, Object> result = onboardingMcpTool.completeOnboarding("GHOST");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }
}