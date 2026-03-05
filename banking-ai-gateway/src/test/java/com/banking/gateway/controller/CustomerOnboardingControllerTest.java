package com.banking.gateway.controller;

import com.banking.common.exception.BankingExceptions.*;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerOnboardingController.class)
@Import({GlobalExceptionHandler.class})
@DisplayName("CustomerOnboardingController — slice tests")
class CustomerOnboardingControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CustomerOnboardingService onboardingService;

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private static final String API_KEY = "banking-demo-key-2024";
    private static final String BASE    = "/api/v1/onboarding";

    private OnboardingResponse onboardingResp() {
        return new OnboardingResponse("CUST-00000001", "INITIATED",
                "Onboarding initiated successfully.", "SUBMIT_DOCUMENTS");
    }

    private CustomerResponse customerResp(String id, String kyc, String ob) {
        return new CustomerResponse(
            id, "Alice", "Johnson", "Alice Johnson",
            LocalDate.of(1990, 5, 15), "FEMALE",
            "alice@example.com", "+447700900001",
            "British", "ABCDE1234F", "PAN_CARD", LocalDate.of(2030, 12, 31),
            kyc, ob, "LOW", null,
            "SALARIED", "Tech Corp", new BigDecimal("80000"), "GBP",
            LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private String validOnboardingJson() throws Exception {
        return objectMapper.writeValueAsString(new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
            "ABCDE1234F", null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 12, 31),
            new AddressRequest("123 St", null, "London", "England", "EC1A 1BB", "GBR"),
            Customer.EmploymentType.SALARIED, "Tech Corp",
            new BigDecimal("80000"), "GBP", "SAVINGS"
        ));
    }

    // ─── POST /customers ──────────────────────────────────────────────────────

    @Nested @DisplayName("POST /customers")
    class InitiateOnboarding {

        @Test @WithMockUser(roles = "API_USER")
        void validRequest_returns201WithCustomerId() throws Exception {
            when(onboardingService.initiateOnboarding(any())).thenReturn(onboardingResp());

            mockMvc.perform(post(BASE + "/customers")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validOnboardingJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value("CUST-00000001"))
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andExpect(jsonPath("$.data.nextStep").value("SUBMIT_DOCUMENTS"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void duplicateEmail_returns409() throws Exception {
            when(onboardingService.initiateOnboarding(any()))
                    .thenThrow(new DuplicateResourceException("Customer", "email: alice@example.com"));

            mockMvc.perform(post(BASE + "/customers")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validOnboardingJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void kycValidationFails_returns422() throws Exception {
            when(onboardingService.initiateOnboarding(any()))
                    .thenThrow(new KycFailedException("Under 18 years old"));

            mockMvc.perform(post(BASE + "/customers")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validOnboardingJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("KYC_FAILED"));
        }
    }

    // ─── GET /customers/{id} ──────────────────────────────────────────────────

    @Nested @DisplayName("GET /customers/{customerId}")
    class GetCustomer {

        @Test @WithMockUser(roles = "API_USER")
        void existingCustomer_returns200() throws Exception {
            when(onboardingService.getCustomer("CUST-001"))
                    .thenReturn(customerResp("CUST-001", "VERIFIED", "COMPLETED"));

            mockMvc.perform(get(BASE + "/customers/CUST-001")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void missingCustomer_returns404() throws Exception {
            when(onboardingService.getCustomer("GHOST"))
                    .thenThrow(new CustomerNotFoundException("GHOST"));

            mockMvc.perform(get(BASE + "/customers/GHOST")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ─── PATCH /customers/{id}/kyc ────────────────────────────────────────────

    @Nested @DisplayName("PATCH /customers/{customerId}/kyc")
    class UpdateKyc {

        @Test @WithMockUser(roles = "API_USER")
        void approveKyc_returns200WithVerifiedStatus() throws Exception {
            when(onboardingService.updateKycStatus(any()))
                    .thenReturn(customerResp("CUST-001", "VERIFIED", "KYC_VERIFIED"));

            mockMvc.perform(patch(BASE + "/customers/CUST-001/kyc")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"kycStatus\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void rejectKyc_returns200WithRejectedStatus() throws Exception {
            when(onboardingService.updateKycStatus(any()))
                    .thenReturn(customerResp("CUST-001", "REJECTED", "REJECTED"));

            mockMvc.perform(patch(BASE + "/customers/CUST-001/kyc")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"kycStatus\":\"REJECTED\",\"rejectionReason\":\"ID unclear\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("REJECTED"));
        }
    }

    // ─── POST /customers/{id}/complete ────────────────────────────────────────

    @Nested @DisplayName("POST /customers/{customerId}/complete")
    class CompleteOnboarding {

        @Test @WithMockUser(roles = "API_USER")
        void kycVerified_returns200() throws Exception {
            when(onboardingService.completeOnboarding("CUST-001"))
                    .thenReturn(customerResp("CUST-001", "VERIFIED", "COMPLETED"));

            mockMvc.perform(post(BASE + "/customers/CUST-001/complete")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStatus").value("COMPLETED"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void kycNotVerified_returns400() throws Exception {
            when(onboardingService.completeOnboarding("CUST-001"))
                    .thenThrow(new OnboardingException("KYC is not verified"));

            mockMvc.perform(post(BASE + "/customers/CUST-001/complete")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ONBOARDING_FAILED"));
        }
    }

    // ─── GET /kyc/pending ─────────────────────────────────────────────────────

    @Nested @DisplayName("GET /kyc/pending")
    class PendingKyc {

        @Test @WithMockUser(roles = "API_USER")
        void returns200WithPendingCustomers() throws Exception {
            CustomerSummary summary = new CustomerSummary("CUST-001", "Alice Johnson",
                    "alice@example.com", "+447700900001", "UNDER_REVIEW", "INITIATED", LocalDateTime.now());
            when(onboardingService.getPendingKycCustomers(any()))
                    .thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get(BASE + "/kyc/pending")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerId").value("CUST-001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }
}
