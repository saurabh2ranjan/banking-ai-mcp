package com.banking.gateway.controller;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.service.PaymentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

// ─── AccountController tests ──────────────────────────────────────────────────

@WebMvcTest(controllers = {AccountController.class, PaymentController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("AccountController and PaymentController — slice tests")
class AccountAndPaymentControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean  AccountService  accountService;
    @MockitoBean  PaymentService  paymentService;

    private static final String API_KEY = "banking-demo-key-2024";

    // ─── Account fixtures ─────────────────────────────────────────────────────

    private AccountResponse accountResp(String id, String status) {
        return new AccountResponse(id, "GB29BANK0001", "CUST-001", "My Savings",
                "SAVINGS", status, new BigDecimal("10000"), new BigDecimal("10000"),
                BigDecimal.ZERO, "GBP", new BigDecimal("10000"), new BigDecimal("5000"),
                new BigDecimal("500"), new BigDecimal("0.035"),
                LocalDate.now(), null, LocalDateTime.now(), LocalDateTime.now());
    }

    private BalanceResponse balanceResp() {
        return new BalanceResponse("ACC-001", "GB29BANK0001",
                new BigDecimal("10000"), new BigDecimal("8000"),
                new BigDecimal("2000"), "GBP", "ACTIVE");
    }

    // ─── Payment fixtures ─────────────────────────────────────────────────────

    private PaymentResponse paymentResp(String status) {
        return new PaymentResponse(
                "PAY-001", "IMPS-123456-ABCDEF", "CUST-001",
                "ACC-SRC", "ACC-DST", new BigDecimal("500"), "GBP",
                "IMPS", status, "Rent", LocalDateTime.now(),
                "COMPLETED".equals(status) ? LocalDateTime.now() : null,
                null, null, null);
    }

    private String openAccountJson() throws Exception {
        return objectMapper.writeValueAsString(new OpenAccountRequest(
                "CUST-001", Account.AccountType.SAVINGS, "GBP",
                "My Savings", new BigDecimal("5000"), null, null));
    }

    private String initiatePaymentJson() throws Exception {
        return objectMapper.writeValueAsString(new InitiatePaymentRequest(
                "CUST-001", "ACC-SRC", "ACC-DST",
                new BigDecimal("500.00"), "GBP",
                Payment.PaymentType.IMPS, "Rent payment"));
    }

    // ─── POST /api/v1/accounts ────────────────────────────────────────────────

    @Nested @DisplayName("POST /api/v1/accounts — openAccount")
    class OpenAccount {

        @Test @WithMockUser(roles = "API_USER")
        void validRequest_returns201() throws Exception {
            when(accountService.openAccount(any())).thenReturn(accountResp("ACC-001", "ACTIVE"));

            mockMvc.perform(post("/api/v1/accounts")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openAccountJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value("ACC-001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void kycNotVerified_returns400() throws Exception {
            when(accountService.openAccount(any()))
                    .thenThrow(new OnboardingException("KYC is not verified"));

            mockMvc.perform(post("/api/v1/accounts")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openAccountJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ONBOARDING_FAILED"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    // ─── GET /api/v1/accounts/{id} ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/v1/accounts/{accountId}")
    class GetAccount {

        @Test @WithMockUser(roles = "API_USER")
        void found_returns200() throws Exception {
            when(accountService.getAccount("ACC-001")).thenReturn(accountResp("ACC-001", "ACTIVE"));

            mockMvc.perform(get("/api/v1/accounts/ACC-001")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("ACC-001"))
                .andExpect(jsonPath("$.data.balance").value(10000));
        }

        @Test @WithMockUser(roles = "API_USER")
        void notFound_returns404() throws Exception {
            when(accountService.getAccount("GHOST"))
                    .thenThrow(new AccountNotFoundException("GHOST"));

            mockMvc.perform(get("/api/v1/accounts/GHOST")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ─── GET /api/v1/accounts/{id}/balance ───────────────────────────────────

    @Nested @DisplayName("GET /api/v1/accounts/{id}/balance")
    class GetBalance {

        @Test @WithMockUser(roles = "API_USER")
        void returns200WithBalanceBreakdown() throws Exception {
            when(accountService.getBalance("ACC-001")).thenReturn(balanceResp());

            mockMvc.perform(get("/api/v1/accounts/ACC-001/balance")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(10000))
                .andExpect(jsonPath("$.data.availableBalance").value(8000))
                .andExpect(jsonPath("$.data.holdAmount").value(2000))
                .andExpect(jsonPath("$.data.currency").value("GBP"));
        }
    }

    // ─── GET /api/v1/accounts?customerId=... ─────────────────────────────────

    @Nested @DisplayName("GET /api/v1/accounts — getCustomerAccounts")
    class GetCustomerAccounts {

        @Test @WithMockUser(roles = "API_USER")
        void returns200WithAccountList() throws Exception {
            AccountSummary s = new AccountSummary("ACC-001", "GB29BANK0001", "SAVINGS",
                    "ACTIVE", new BigDecimal("10000"), "GBP");
            when(accountService.getCustomerAccounts("CUST-001")).thenReturn(List.of(s));

            mockMvc.perform(get("/api/v1/accounts")
                    .param("customerId", "CUST-001")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].accountId").value("ACC-001"));
        }
    }

    // ─── POST /api/v1/accounts/{id}/block ────────────────────────────────────

    @Nested @DisplayName("POST /api/v1/accounts/{id}/block")
    class BlockAccount {

        @Test @WithMockUser(roles = "API_USER")
        void blocksAccount_returns200() throws Exception {
            when(accountService.blockAccount("ACC-001", "Fraud detected"))
                    .thenReturn(accountResp("ACC-001", "BLOCKED"));

            mockMvc.perform(post("/api/v1/accounts/ACC-001/block")
                    .param("reason", "Fraud detected")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.message").value("Account blocked"));
        }
    }

    // ─── POST /api/v1/accounts/{id}/unblock ──────────────────────────────────

    @Nested @DisplayName("POST /api/v1/accounts/{id}/unblock")
    class UnblockAccount {

        @Test @WithMockUser(roles = "API_USER")
        void unblocksAccount_returns200() throws Exception {
            when(accountService.unblockAccount("ACC-001"))
                    .thenReturn(accountResp("ACC-001", "ACTIVE"));

            mockMvc.perform(post("/api/v1/accounts/ACC-001/unblock")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Account unblocked"));
        }
    }

    // ─── POST /api/v1/payments ────────────────────────────────────────────────

    @Nested @DisplayName("POST /api/v1/payments — initiatePayment")
    class InitiatePayment {

        @Test @WithMockUser(roles = "API_USER")
        void validRequest_returns201() throws Exception {
            when(paymentService.initiatePayment(any())).thenReturn(paymentResp("PENDING_FRAUD_CHECK"));

            mockMvc.perform(post("/api/v1/payments")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initiatePaymentJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value("PAY-001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_FRAUD_CHECK"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void inactiveSourceAccount_returns422() throws Exception {
            when(paymentService.initiatePayment(any()))
                    .thenThrow(new AccountInactiveException("ACC-SRC"));

            mockMvc.perform(post("/api/v1/payments")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initiatePaymentJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void insufficientFunds_returns422() throws Exception {
            when(paymentService.initiatePayment(any()))
                    .thenThrow(new InsufficientFundsException("ACC-SRC"));

            mockMvc.perform(post("/api/v1/payments")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initiatePaymentJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
        }
    }

    // ─── POST /api/v1/payments/{id}/process ──────────────────────────────────

    @Nested @DisplayName("POST /api/v1/payments/{id}/process")
    class ProcessPayment {

        @Test @WithMockUser(roles = "API_USER")
        void validPayment_returns200Completed() throws Exception {
            when(paymentService.processPayment("PAY-001")).thenReturn(paymentResp("COMPLETED"));

            mockMvc.perform(post("/api/v1/payments/PAY-001/process")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void notFound_returns404() throws Exception {
            when(paymentService.processPayment("GHOST"))
                    .thenThrow(new PaymentNotFoundException("GHOST"));

            mockMvc.perform(post("/api/v1/payments/GHOST/process")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
        }
    }

    // ─── GET /api/v1/payments/{id} ────────────────────────────────────────────

    @Nested @DisplayName("GET /api/v1/payments/{paymentId}")
    class GetPayment {

        @Test @WithMockUser(roles = "API_USER")
        void found_returns200() throws Exception {
            when(paymentService.getPayment("PAY-001")).thenReturn(paymentResp("COMPLETED"));

            mockMvc.perform(get("/api/v1/payments/PAY-001")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value("PAY-001"))
                .andExpect(jsonPath("$.data.amount").value(500));
        }
    }

    // ─── GET /api/v1/payments?accountId=... ──────────────────────────────────

    @Nested @DisplayName("GET /api/v1/payments — getPayments")
    class GetPayments {

        @Test @WithMockUser(roles = "API_USER")
        void returns200WithPage() throws Exception {
            PaymentSummary s = new PaymentSummary("PAY-001", "REF-001",
                    new BigDecimal("500"), "GBP", "IMPS", "COMPLETED", LocalDateTime.now());
            when(paymentService.getAccountPayments(eq("ACC-SRC"), any()))
                    .thenReturn(new PageImpl<>(List.of(s)));

            mockMvc.perform(get("/api/v1/payments")
                    .param("accountId", "ACC-SRC")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ─── POST /api/v1/payments/{id}/reverse ──────────────────────────────────

    @Nested @DisplayName("POST /api/v1/payments/{id}/reverse")
    class ReversePayment {

        @Test @WithMockUser(roles = "API_USER")
        void completedPayment_returns200() throws Exception {
            when(paymentService.reversePayment("PAY-001", "Customer dispute"))
                    .thenReturn(paymentResp("COMPLETED"));

            mockMvc.perform(post("/api/v1/payments/PAY-001/reverse")
                    .param("reason", "Customer dispute")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment reversed"));
        }

        @Test @WithMockUser(roles = "API_USER")
        void nonCompletedPayment_returns400() throws Exception {
            when(paymentService.reversePayment("PAY-001", "reason"))
                    .thenThrow(new PaymentException("Only completed payments can be reversed"));

            mockMvc.perform(post("/api/v1/payments/PAY-001/reverse")
                    .param("reason", "reason")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_FAILED"));
        }
    }

    // ─── GET /api/v1/payments/accounts/{id}/daily-summary ────────────────────

    @Nested @DisplayName("GET /api/v1/payments/accounts/{id}/daily-summary")
    class DailySummary {

        @Test @WithMockUser(roles = "API_USER")
        void returns200WithSummary() throws Exception {
            DailySpendingSummary s = new DailySpendingSummary("ACC-SRC",
                    new BigDecimal("3000"), 3, new BigDecimal("1000"),
                    new BigDecimal("1500"), "GBP");
            when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(s);

            mockMvc.perform(get("/api/v1/payments/accounts/ACC-SRC/daily-summary")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpentToday").value(3000))
                .andExpect(jsonPath("$.data.transactionCount").value(3))
                .andExpect(jsonPath("$.data.currency").value("GBP"));
        }
    }
}
