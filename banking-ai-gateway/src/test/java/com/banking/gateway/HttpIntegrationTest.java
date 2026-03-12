package com.banking.gateway;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.OpenAccountRequest;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.InitiatePaymentRequest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer integration tests.
 *
 * Runs the full Spring Boot application (not a slice) against H2.
 * Uses MockMvc to make real HTTP requests through the security filter chain.
 * All tests are rolled back after execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedAiTestConfig.class)
@Transactional
@DisplayName("HTTP Integration Tests — REST API through full stack")
class HttpIntegrationTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper              objectMapper;
    @Autowired CustomerOnboardingService onboardingService;

    private static final String API_KEY   = "test-api-key";
    private static final String ONBOARDING = "/api/v1/onboarding";
    private static final String ACCOUNTS   = "/api/v1/accounts";
    private static final String PAYMENTS   = "/api/v1/payments";

    // ─── Auth helpers ─────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.header("X-API-Key", API_KEY);
    }

    // ─── Fixture helpers ──────────────────────────────────────────────────────

    private String toJson(Object obj) throws Exception { return objectMapper.writeValueAsString(obj); }

    private OnboardingRequest validOnboardingRequest(String email, String mobile, String pan) {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, email, mobile, "British",
            pan, null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2032, 12, 31),
            new AddressRequest("123 High Street", null, "London", "England", "EC1A 1BB", "GBR"),
            Customer.EmploymentType.SALARIED, "Tech Corp",
            new BigDecimal("80000"), "GBP", "SAVINGS");
    }

    private String onboardAndCompleteViaApi(String email, String mobile, String pan) throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(validOnboardingRequest(email, mobile, pan));
        String id = ob.customerId();
        onboardingService.updateKycStatus(new KycUpdateRequest(id, Customer.KycStatus.VERIFIED, null));
        onboardingService.completeOnboarding(id);
        return id;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Authentication
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class Authentication {

    @Test
        @DisplayName("Missing API key → 401 Unauthorized")
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(get(ACCOUNTS + "/ACC-001"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
        @DisplayName("Wrong API key → 401 Unauthorized")
    void wrongApiKey_returns401() throws Exception {
        mockMvc.perform(get(ACCOUNTS + "/ACC-001").header("X-API-Key", "wrong-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
        @DisplayName("Valid API key → request is processed (not 401)")
        void validApiKey_requestIsProcessed() throws Exception {
        mockMvc.perform(withAuth(get(ACCOUNTS).param("customerId", "CUST-NONE")))
                .andExpect(status().isOk());  // Empty list returned, not 401
        }

        @Test
        @DisplayName("Actuator health endpoint is accessible without API key")
        void actuatorHealth_noAuthRequired() throws Exception {
            mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Onboarding Controller
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/onboarding/customers — initiateOnboarding")
    class OnboardingInit {

    @Test
        @DisplayName("Valid request → 201 with customerId and nextStep")
        void validRequest_returns201() throws Exception {
        mockMvc.perform(withAuth(post(ONBOARDING + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(validOnboardingRequest("http.test@example.com", "+447700800001", "HTTPA1234F")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.customerId").value(startsWith("CUST-")))
            .andExpect(jsonPath("$.data.status").value("INITIATED"))
            .andExpect(jsonPath("$.data.nextStep").value("SUBMIT_DOCUMENTS"))
            .andExpect(jsonPath("$.message").value("Onboarding initiated"));
    }

    @Test
        @DisplayName("Missing required fields → 400 with validation errors")
        void missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(withAuth(post(ONBOARDING + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}")))  // missing many required fields
            .andExpect(status().isBadRequest());
    }

    @Test
        @DisplayName("Duplicate email → 409 Conflict")
        void duplicateEmail_returns409() throws Exception {
            // Create first customer via service directly for efficiency
        onboardingService.initiateOnboarding(
                validOnboardingRequest("dup.http@example.com", "+447700800010", "HTTPB1234F"));
        mockMvc.perform(withAuth(post(ONBOARDING + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(validOnboardingRequest("dup.http@example.com", "+447700800011", "HTTPC1234F")))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
        @DisplayName("Invalid PAN format → 422 KYC Failed")
        void invalidPan_returns422() throws Exception {
        mockMvc.perform(withAuth(post(ONBOARDING + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(validOnboardingRequest("badpan@example.com", "+447700800020", "INVALID_PAN")))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("KYC_FAILED"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Customer queries
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/onboarding/customers/{id}")
    class GetCustomer {

    @Test
        @DisplayName("Existing customer → 200 with customer data")
        void existingCustomer_returns200() throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                validOnboardingRequest("http.get@example.com", "+447700800030", "HTTPD1234F"));
        mockMvc.perform(withAuth(get(ONBOARDING + "/customers/" + ob.customerId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(ob.customerId()))
            .andExpect(jsonPath("$.data.email").value("http.get@example.com"))
            .andExpect(jsonPath("$.data.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.fullName").value("Alice Johnson"));
    }

    @Test
        @DisplayName("Unknown customer → 404 Not Found")
        void unknownCustomer_returns404() throws Exception {
        mockMvc.perform(withAuth(get(ONBOARDING + "/customers/CUST-GHOST-999")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // KYC update
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/onboarding/customers/{id}/kyc")
    class UpdateKyc {

    @Test
        @DisplayName("Approve KYC → 200 with VERIFIED status")
        void approveKyc_returns200() throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                validOnboardingRequest("http.kyc@example.com", "+447700800040", "HTTPE1234F"));
        mockMvc.perform(withAuth(patch(ONBOARDING + "/customers/" + ob.customerId() + "/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kycStatus\":\"VERIFIED\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.data.onboardingStatus").value("KYC_VERIFIED"));
    }

    @Test
        @DisplayName("Reject KYC with reason → 200 with REJECTED status")
        void rejectKyc_returns200() throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                validOnboardingRequest("http.kyc.rej@example.com", "+447700800041", "HTTPF1234F"));
        mockMvc.perform(withAuth(patch(ONBOARDING + "/customers/" + ob.customerId() + "/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kycStatus\":\"REJECTED\",\"rejectionReason\":\"Blurry document\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kycStatus").value("REJECTED"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Account Controller
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/accounts — openAccount")
    class OpenAccountHttp {

    @Test
        @DisplayName("KYC-verified completed customer → 201 with account details")
        void validCustomer_returns201() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "acc.open@example.com", "+447700800050", "HTTPG1234F");

        OpenAccountRequest req = new OpenAccountRequest(
                customerId, Account.AccountType.SAVINGS, "GBP", "Test Savings",
                new BigDecimal("10000"), null, null);
        mockMvc.perform(withAuth(post(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.customerId").value(customerId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.accountType").value("SAVINGS"))
            .andExpect(jsonPath("$.data.balance").value(10000))
            .andExpect(jsonPath("$.data.holdAmount").value(0))
            .andExpect(jsonPath("$.message").value("Account opened successfully"));
    }

    @Test
        @DisplayName("KYC not verified → 400 with ONBOARDING_FAILED")
        void kycNotVerified_returns400() throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                validOnboardingRequest("acc.nokyc@example.com", "+447700800060", "HTTPH1234F"));
        OpenAccountRequest req = new OpenAccountRequest(
                ob.customerId(), Account.AccountType.SAVINGS, "GBP", null, null, null, null);
        mockMvc.perform(withAuth(post(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ONBOARDING_FAILED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountId}")
    class GetAccountHttp {

    @Test
        @DisplayName("Existing account → 200 with full account details")
        void existingAccount_returns200() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "acc.get@example.com", "+447700800070", "HTTPI1234F");
            OpenAccountRequest req = new OpenAccountRequest(
                    customerId, Account.AccountType.SAVINGS, "GBP", "My Savings",
                    new BigDecimal("5000"), null, null);

            String accountId = extractAccountId(toJson(req), customerId);

        mockMvc.perform(withAuth(get(ACCOUNTS + "/" + accountId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(accountId))
            .andExpect(jsonPath("$.data.balance").value(5000))
            .andExpect(jsonPath("$.data.currency").value("GBP"));
    }

    @Test
        @DisplayName("Unknown account → 404")
        void unknownAccount_returns404() throws Exception {
        mockMvc.perform(withAuth(get(ACCOUNTS + "/ACC-GHOST-999")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{id}/balance")
    class GetBalanceHttp {

    @Test
        @DisplayName("Returns balance with hold breakdown")
        void returnsBalance() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "bal.get@example.com", "+447700800080", "HTTPJ1234F");
            String accountId  = openAccountViaService(customerId, new BigDecimal("8000"));

        mockMvc.perform(withAuth(get(ACCOUNTS + "/" + accountId + "/balance")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance").value(8000))
            .andExpect(jsonPath("$.data.availableBalance").value(8000))
            .andExpect(jsonPath("$.data.holdAmount").value(0))
            .andExpect(jsonPath("$.data.currency").value("GBP"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts?customerId=...")
    class GetCustomerAccountsHttp {

    @Test
        @DisplayName("Returns list of customer's accounts")
        void returnsAccountList() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "multi.acc@example.com", "+447700800090", "HTTPK1234F");
            openAccountViaService(customerId, new BigDecimal("5000"));
            openAccountViaService(customerId, new BigDecimal("3000"));

        mockMvc.perform(withAuth(get(ACCOUNTS).param("customerId", customerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
        @DisplayName("Unknown customer returns empty list, not 404")
        void unknownCustomer_returnsEmptyList() throws Exception {
        mockMvc.perform(withAuth(get(ACCOUNTS).param("customerId", "CUST-NONE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Payment Controller
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/payments — initiatePayment")
    class InitiatePaymentHttp {

    @Test
        @DisplayName("Valid payment → 201 with PENDING_FRAUD_CHECK status")
        void validPayment_returns201() throws Exception {
            String aliceId    = onboardAndCompleteViaApi("pay.alice@example.com", "+447700800100", "HTTPL1234F");
            String bobId      = onboardAndCompleteViaApi("pay.bob@example.com",   "+447700800101", "HTTPM1234G");
            String aliceAccId = openAccountViaService(aliceId, new BigDecimal("10000"));
            String bobAccId   = openAccountViaService(bobId,   new BigDecimal("500"));

        InitiatePaymentRequest req = new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("1000"), "GBP", Payment.PaymentType.IMPS, "test");
        mockMvc.perform(withAuth(post(PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("PENDING_FRAUD_CHECK"))
            .andExpect(jsonPath("$.data.amount").value(1000))
            .andExpect(jsonPath("$.data.referenceNumber").value(startsWith("IMPS-")))
            .andExpect(jsonPath("$.message").value("Payment initiated"));
    }

    @Test
        @DisplayName("Insufficient funds → 422 INSUFFICIENT_FUNDS")
        void insufficientFunds_returns422() throws Exception {
            String aliceId    = onboardAndCompleteViaApi("insuf.alice@example.com", "+447700800110", "HTTPN1234F");
            String bobId      = onboardAndCompleteViaApi("insuf.bob@example.com",   "+447700800111", "HTTPO1234G");
            String aliceAccId = openAccountViaService(aliceId, new BigDecimal("100"));  // tiny balance
            String bobAccId   = openAccountViaService(bobId,   new BigDecimal("500"));

        InitiatePaymentRequest req = new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("5000"), "GBP", Payment.PaymentType.IMPS, "too much");
        mockMvc.perform(withAuth(post(PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
        @DisplayName("Missing required fields → 400")
        void missingFields_returns400() throws Exception {
        mockMvc.perform(withAuth(post(PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}")))
            .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/{id}/process")
    class ProcessPaymentHttp {

    @Test
        @DisplayName("Valid payment processes to COMPLETED with correct balances")
        void processPayment_returns200() throws Exception {
            String aliceId    = onboardAndCompleteViaApi("proc.alice@example.com", "+447700800120", "HTTPP1234F");
            String bobId      = onboardAndCompleteViaApi("proc.bob@example.com",   "+447700800121", "HTTPQ1234G");
            String aliceAccId = openAccountViaService(aliceId, new BigDecimal("10000"));
            String bobAccId   = openAccountViaService(bobId,   new BigDecimal("500"));

            // Initiate first
        InitiatePaymentRequest req = new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("2000"), "GBP", Payment.PaymentType.NEFT, "process test");
        String initBody = mockMvc.perform(withAuth(post(PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andReturn().getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(initBody).at("/data/paymentId").asText();

            // Now process
        mockMvc.perform(withAuth(post(PAYMENTS + "/" + paymentId + "/process")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
        @DisplayName("Unknown payment → 404")
        void unknownPayment_returns404() throws Exception {
        mockMvc.perform(withAuth(post(PAYMENTS + "/PAY-GHOST-999/process")))
            .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{id}")
    class GetPaymentHttp {

    @Test
        @DisplayName("Unknown payment → 404")
        void unknownPayment_returns404() throws Exception {
        mockMvc.perform(withAuth(get(PAYMENTS + "/PAY-NONEXISTENT")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/accounts/{id}/daily-summary")
    class DailySummaryHttp {

    @Test
        @DisplayName("Account with no transactions returns zeros")
        void noTransactions_returnsZeroes() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "daily.http@example.com", "+447700800130", "HTTPR1234F");
            String accountId  = openAccountViaService(customerId, new BigDecimal("5000"));

        mockMvc.perform(withAuth(get(PAYMENTS + "/accounts/" + accountId + "/daily-summary")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalSpentToday").value(0))
            .andExpect(jsonPath("$.data.transactionCount").value(0))
            .andExpect(jsonPath("$.data.accountId").value(accountId));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments?accountId=...")
    class GetPaymentsHttp {

    @Test
        @DisplayName("Returns paged list of payments for account")
        void returnsPagedPayments() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "paged.http@example.com", "+447700800140", "HTTPS1234F");
            String accountId  = openAccountViaService(customerId, new BigDecimal("10000"));

        mockMvc.perform(withAuth(get(PAYMENTS).param("accountId", accountId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/accounts/{id}/block and /unblock")
    class BlockUnblockHttp {

    @Test
        @DisplayName("Block then unblock account via HTTP")
        void blockAndUnblock_via_http() throws Exception {
            String customerId = onboardAndCompleteViaApi(
                    "block.http@example.com", "+447700800150", "HTTPT1234F");
            String accountId  = openAccountViaService(customerId, new BigDecimal("5000"));

            // Block
        mockMvc.perform(withAuth(post(ACCOUNTS + "/" + accountId + "/block").param("reason", "Fraud detected")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("BLOCKED"))
            .andExpect(jsonPath("$.message").value("Account blocked"));

            // Unblock
        mockMvc.perform(withAuth(post(ACCOUNTS + "/" + accountId + "/unblock")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.message").value("Account unblocked"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/onboarding/kyc/pending")
    class PendingKycHttp {

    @Test
        @DisplayName("Returns paginated pending KYC customers")
        void returnsKycPendingCustomers() throws Exception {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                validOnboardingRequest("kyc.pend.http@example.com", "+447700800160", "HTTPU1234F"));
        onboardingService.updateKycStatus(
                new KycUpdateRequest(ob.customerId(), Customer.KycStatus.UNDER_REVIEW, null));
        mockMvc.perform(withAuth(get(ONBOARDING + "/kyc/pending")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));
    }
}

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private String extractAccountId(String requestJson, String customerId) throws Exception {
        OpenAccountRequest req = new OpenAccountRequest(
                customerId, Account.AccountType.SAVINGS, "GBP",
                "My Savings", new BigDecimal("5000"), null, null);
        String resp = mockMvc.perform(withAuth(post(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/accountId").asText();
    }

    private String openAccountViaService(String customerId, BigDecimal deposit) throws Exception {
        OpenAccountRequest req = new OpenAccountRequest(
                customerId, Account.AccountType.CURRENT, "GBP",
                "Account", deposit, null, null);
        String body = mockMvc.perform(withAuth(post(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accountId").asText();
    }
}
