package com.banking.gateway.controller;

import com.banking.gateway.security.ApiKeyAuthFilter;
import com.banking.gateway.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExceptionTestController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ApiKeyAuthFilter apiKeyAuthFilter;

    // ── BankingException → correct HTTP status and errorCode ─────────────────

    @Test @WithMockUser(roles = "API_USER")
    void customerNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/customer-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test @WithMockUser(roles = "API_USER")
    void accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/account-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void duplicateResource_returns409() throws Exception {
        mockMvc.perform(get("/test/duplicate-resource"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void kycFailed_returns422() throws Exception {
        mockMvc.perform(get("/test/kyc-failed"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("KYC_FAILED"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void onboardingFailed_returns400() throws Exception {
        mockMvc.perform(get("/test/onboarding-failed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ONBOARDING_FAILED"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void insufficientFunds_returns422() throws Exception {
        mockMvc.perform(get("/test/insufficient-funds"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    // ── Non-BankingException handlers ─────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void optimisticLock_returns409() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_FAILURE"))
                .andExpect(jsonPath("$.message").value("Resource was concurrently modified. Please retry."));
    }

    @Test @WithMockUser(roles = "API_USER")
    void illegalArgument_returns400() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Invalid value provided"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void unexpectedException_returns500() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please contact support."));
    }

    @Test @WithMockUser(roles = "API_USER")
    void missingRequestBody_returns400() throws Exception {
        mockMvc.perform(post("/test/missing-body")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void validationFailure_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("name must not be blank")));
    }

    // ── Response body structure ───────────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void errorResponse_alwaysContainsRequiredFields() throws Exception {
        mockMvc.perform(get("/test/customer-not-found"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test @WithMockUser(roles = "API_USER")
    void errorResponse_neverExposesInternalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please contact support."));
    }

    @Test @WithMockUser(roles = "API_USER")
    void errorResponse_successIsAlwaysFalse() throws Exception {
        mockMvc.perform(get("/test/account-not-found"))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/test/unexpected"))
                .andExpect(jsonPath("$.success").value(false));
    }
}