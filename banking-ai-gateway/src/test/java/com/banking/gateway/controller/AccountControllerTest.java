package com.banking.gateway.controller;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.*;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
class AccountControllerTest {

    @MockitoBean ApiKeyAuthFilter apiKeyAuthFilter;
    @Autowired  MockMvc           mockMvc;
    @Autowired  ObjectMapper      objectMapper;
    @MockitoBean AccountService   accountService;

    private static final String API_KEY = "banking-demo-key-2024";
    private static final String BASE = "/api/v1/accounts";

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

    private String openAccountJson() throws Exception {
        return objectMapper.writeValueAsString(new OpenAccountRequest(
                "CUST-001", Account.AccountType.SAVINGS, "GBP",
                "My Savings", new BigDecimal("5000"), null, null));
    }

    // ── POST /api/v1/accounts ──────────────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void openAccount_validRequest_returns201() throws Exception {
        when(accountService.openAccount(any())).thenReturn(accountResp("ACC-001", "ACTIVE"));
        mockMvc.perform(post(BASE).header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(openAccountJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accountId").value("ACC-001"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void openAccount_kycNotVerified_returns400() throws Exception {
        when(accountService.openAccount(any())).thenThrow(new OnboardingException("KYC is not verified"));
        mockMvc.perform(post(BASE).header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(openAccountJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ONBOARDING_FAILED"));
    }

    @Test @WithMockUser(roles = "API_USER")
    void openAccount_missingBody_returns400() throws Exception {
        mockMvc.perform(post(BASE).header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/accounts/{id} ──────────────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getAccount_found_returns200() throws Exception {
        when(accountService.getAccount("ACC-001")).thenReturn(accountResp("ACC-001", "ACTIVE"));
        mockMvc.perform(get(BASE + "/ACC-001").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value("ACC-001"))
            .andExpect(jsonPath("$.data.balance").value(10000));
    }

    @Test @WithMockUser(roles = "API_USER")
    void getAccount_notFound_returns404() throws Exception {
        when(accountService.getAccount("GHOST")).thenThrow(new AccountNotFoundException("GHOST"));
        mockMvc.perform(get(BASE + "/GHOST").header("X-API-Key", API_KEY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ── GET /api/v1/accounts/{id}/balance ──────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getBalance_returns200WithBreakdown() throws Exception {
        when(accountService.getBalance("ACC-001")).thenReturn(balanceResp());
        mockMvc.perform(get(BASE + "/ACC-001/balance").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance").value(10000))
            .andExpect(jsonPath("$.data.availableBalance").value(8000))
            .andExpect(jsonPath("$.data.holdAmount").value(2000))
            .andExpect(jsonPath("$.data.currency").value("GBP"));
    }

    // ── GET /api/v1/accounts?customerId=... ────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void getCustomerAccounts_returns200WithList() throws Exception {
        AccountSummary s = new AccountSummary("ACC-001", "GB29BANK0001", "SAVINGS", "ACTIVE",
                new BigDecimal("10000"), "GBP");
        when(accountService.getCustomerAccounts("CUST-001")).thenReturn(List.of(s));
        mockMvc.perform(get(BASE).param("customerId", "CUST-001").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].accountId").value("ACC-001"));
    }

    // ── POST /api/v1/accounts/{id}/block ──────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void blockAccount_returns200WithBlockedStatus() throws Exception {
        when(accountService.blockAccount("ACC-001", "Fraud detected")).thenReturn(accountResp("ACC-001", "BLOCKED"));
        mockMvc.perform(post(BASE + "/ACC-001/block")
                .param("reason", "Fraud detected").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("BLOCKED"))
            .andExpect(jsonPath("$.message").value("Account blocked"));
    }

    // ── POST /api/v1/accounts/{id}/unblock ────────────────────────────────────

    @Test @WithMockUser(roles = "API_USER")
    void unblockAccount_returns200WithActiveStatus() throws Exception {
        when(accountService.unblockAccount("ACC-001")).thenReturn(accountResp("ACC-001", "ACTIVE"));
        mockMvc.perform(post(BASE + "/ACC-001/unblock").header("X-API-Key", API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.message").value("Account unblocked"));
    }
}
