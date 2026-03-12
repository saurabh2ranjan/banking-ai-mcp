package com.banking.gateway.controller;

import com.banking.common.exception.BankingExceptions.*;
import com.banking.gateway.security.ApiKeyAuthFilter;
import com.banking.gateway.security.SecurityConfig;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.service.PaymentService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
class PaymentControllerTest {

    @MockitoBean ApiKeyAuthFilter apiKeyAuthFilter;
    @Autowired  MockMvc           mockMvc;
    @Autowired  ObjectMapper      objectMapper;
    @MockitoBean PaymentService   paymentService;

    private static final String API_KEY = "banking-demo-key-2024";

    private PaymentResponse paymentResp(String status) {
        return new PaymentResponse("PAY-001", "IMPS-123456-ABCDEF", "CUST-001",
                "ACC-SRC", "ACC-DST", new BigDecimal("500"), "GBP",
                "IMPS", status, "Rent", LocalDateTime.now(),
                "COMPLETED".equals(status) ? LocalDateTime.now() : null,
                null, null, null);
    }

    private String initiatePaymentJson() throws Exception {
        return objectMapper.writeValueAsString(new InitiatePaymentRequest(
                "CUST-001", "ACC-SRC", "ACC-DST",
                new BigDecimal("500.00"), "GBP",
                Payment.PaymentType.IMPS, "Rent payment"));
    }

    // ── POST /api/v1/payments ──────────────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void initiatePayment_validRequest_returns201() throws Exception {
        when(paymentService.initiatePayment(any())).thenReturn(paymentResp("PENDING_FRAUD_CHECK"));
        mockMvc.perform(post("/api/v1/payments").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(initiatePaymentJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.paymentId").value("PAY-001"))
            .andExpect(jsonPath("$.data.status").value("PENDING_FRAUD_CHECK"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void initiatePayment_inactiveSourceAccount_returns422() throws Exception {
        when(paymentService.initiatePayment(any())).thenThrow(new AccountInactiveException("ACC-SRC"));
        mockMvc.perform(post("/api/v1/payments").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(initiatePaymentJson()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void initiatePayment_insufficientFunds_returns422() throws Exception {
        when(paymentService.initiatePayment(any())).thenThrow(new InsufficientFundsException("ACC-SRC"));
        mockMvc.perform(post("/api/v1/payments").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(initiatePaymentJson()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    // ── POST /api/v1/payments/{id}/process ────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void processPayment_validPayment_returns200Completed() throws Exception {
        when(paymentService.processPayment("PAY-001")).thenReturn(paymentResp("COMPLETED"));
        mockMvc.perform(post("/api/v1/payments/PAY-001/process").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void processPayment_notFound_returns404() throws Exception {
        when(paymentService.processPayment("GHOST")).thenThrow(new PaymentNotFoundException("GHOST"));
        mockMvc.perform(post("/api/v1/payments/GHOST/process").header("X-API-Key", API_KEY))
            .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/payments/{id} ──────────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getPayment_found_returns200() throws Exception {
        when(paymentService.getPayment("PAY-001")).thenReturn(paymentResp("COMPLETED"));
        mockMvc.perform(get("/api/v1/payments/PAY-001").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentId").value("PAY-001"))
            .andExpect(jsonPath("$.data.amount").value(500));
    }

    // ── GET /api/v1/payments?accountId=... ────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getPayments_returns200WithPagedResults() throws Exception {
        PaymentSummary s = new PaymentSummary("PAY-001", "REF-001",
                new BigDecimal("500"), "GBP", "IMPS", "COMPLETED", LocalDateTime.now());
        when(paymentService.getAccountPayments(eq("ACC-SRC"), any())).thenReturn(new PageImpl<>(List.of(s)));
        mockMvc.perform(get("/api/v1/payments").param("accountId", "ACC-SRC").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ── POST /api/v1/payments/{id}/reverse ────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void reversePayment_completedPayment_returns200() throws Exception {
        when(paymentService.reversePayment("PAY-001", "Customer dispute")).thenReturn(paymentResp("COMPLETED"));
        mockMvc.perform(post("/api/v1/payments/PAY-001/reverse")
                .param("reason", "Customer dispute").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Payment reversed"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void reversePayment_nonCompletedPayment_returns400() throws Exception {
        when(paymentService.reversePayment("PAY-001", "reason"))
                .thenThrow(new PaymentException("Only completed payments can be reversed"));
        mockMvc.perform(post("/api/v1/payments/PAY-001/reverse")
                .param("reason", "reason").header("X-API-Key", API_KEY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("PAYMENT_FAILED"));
    }

    // ── GET /api/v1/payments/accounts/{id}/daily-summary ─────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getDailySummary_returns200WithTotals() throws Exception {
        DailySpendingSummary s = new DailySpendingSummary("ACC-SRC",
                new BigDecimal("3000"), 3, new BigDecimal("1000"), new BigDecimal("1500"), "GBP");
        when(paymentService.getDailySpendingSummary("ACC-SRC")).thenReturn(s);
        mockMvc.perform(get("/api/v1/payments/accounts/ACC-SRC/daily-summary").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalSpentToday").value(3000))
            .andExpect(jsonPath("$.data.transactionCount").value(3))
            .andExpect(jsonPath("$.data.currency").value("GBP"));
    }
}