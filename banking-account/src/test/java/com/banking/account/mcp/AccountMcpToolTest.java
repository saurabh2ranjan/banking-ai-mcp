package com.banking.account.mcp;

import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.AccountNotFoundException;
import com.banking.common.exception.BankingExceptions.BankingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountMcpToolTest {

    @Mock    AccountService  accountService;
    @InjectMocks AccountMcpTool accountMcpTool;

    // ── helpers ───────────────────────────────────────────────────────────────

    private AccountResponse accountResp(String id, String status) {
        return new AccountResponse(id, "GB29BANK0001", "CUST-001", "My Savings",
                "SAVINGS", status, new BigDecimal("10000"), new BigDecimal("8000"),
                new BigDecimal("2000"), "GBP", new BigDecimal("5000"), new BigDecimal("1000"),
                new BigDecimal("500"), new BigDecimal("0.035"),
                LocalDate.now(), null, LocalDateTime.now(), LocalDateTime.now());
    }

    private BalanceResponse balanceResp(String accountId, BigDecimal balance, BigDecimal available) {
        return new BalanceResponse(accountId, "GB29BANK0001",
                balance, available, balance.subtract(available), "GBP", "ACTIVE");
    }

    // ── open_bank_account ─────────────────────────────────────────────────────

    @Test
    void openBankAccount_validRequest_returnsAccountDetails() {
        when(accountService.openAccount(any())).thenReturn(accountResp("ACC-001", "ACTIVE"));

        Map<String, Object> result = accountMcpTool.openBankAccount(
                "CUST-001", "SAVINGS", "GBP", 5000.0, "My Savings");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("accountId")).isEqualTo("ACC-001");
        assertThat(result.get("status")).isEqualTo("ACTIVE");
        assertThat(result.get("currency")).isEqualTo("GBP");
        assertThat(result.get("message")).isEqualTo("Account successfully opened.");
    }

    @Test
    void openBankAccount_invalidAccountType_returnsError() {
        Map<String, Object> result = accountMcpTool.openBankAccount(
                "CUST-001", "INVALID_TYPE", "GBP", 5000.0, "My Savings");

        assertThat(result).containsKey("error");
        assertThat(result.get("error").toString()).contains("Invalid account type");
    }

    @Test
    void openBankAccount_bankingException_returnsErrorWithCode() {
        BankingException ex = new AccountNotFoundException("CUST-001");
        when(accountService.openAccount(any())).thenThrow(ex);

        Map<String, Object> result = accountMcpTool.openBankAccount(
                "CUST-001", "SAVINGS", "GBP", 5000.0, "My Savings");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    // ── get_account_details ───────────────────────────────────────────────────

    @Test
    void getAccountDetails_existingAccount_returnsFullDetails() {
        when(accountService.getAccount("ACC-001")).thenReturn(accountResp("ACC-001", "ACTIVE"));

        Map<String, Object> result = accountMcpTool.getAccountDetails("ACC-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("accountId")).isEqualTo("ACC-001");
        assertThat(result.get("accountNumber")).isEqualTo("GB29BANK0001");
        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("status")).isEqualTo("ACTIVE");
        assertThat(result.get("currency")).isEqualTo("GBP");
        assertThat(result).containsKeys("balance", "availableBalance", "holdAmount",
                "dailyDebitLimit", "openedDate", "createdAt");
    }

    @Test
    void getAccountDetails_notFound_returnsError() {
        when(accountService.getAccount("GHOST")).thenThrow(new AccountNotFoundException("GHOST"));

        Map<String, Object> result = accountMcpTool.getAccountDetails("GHOST");

        assertThat(result).containsKey("error");
        assertThat(result).containsKey("errorCode");
    }

    @Test
    void getAccountDetails_nullDisplayName_returnsEmptyString() {
        AccountResponse respWithNullName = new AccountResponse(
                "ACC-001", "GB29BANK0001", "CUST-001", null,  // ← null displayName
                "SAVINGS", "ACTIVE", new BigDecimal("10000"), new BigDecimal("8000"),
                new BigDecimal("2000"), "GBP", null, null,
                new BigDecimal("500"), new BigDecimal("0.035"),
                LocalDate.now(), null, LocalDateTime.now(), LocalDateTime.now());
        when(accountService.getAccount("ACC-001")).thenReturn(respWithNullName);

        Map<String, Object> result = accountMcpTool.getAccountDetails("ACC-001");

        assertThat(result.get("displayName")).isEqualTo("");        // null → ""
        assertThat(result.get("dailyDebitLimit")).isEqualTo("N/A"); // null → "N/A"
        assertThat(result.get("singleTransactionLimit")).isEqualTo("N/A");
    }

    // ── get_account_balance ───────────────────────────────────────────────────

    @Test
    void getAccountBalance_existingAccount_returnsBalanceBreakdown() {
        when(accountService.getBalance("ACC-001"))
                .thenReturn(balanceResp("ACC-001", new BigDecimal("10000"), new BigDecimal("8000")));

        Map<String, Object> result = accountMcpTool.getAccountBalance("ACC-001");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("accountId")).isEqualTo("ACC-001");
        assertThat(result.get("balance")).isEqualTo(new BigDecimal("10000"));
        assertThat(result.get("availableBalance")).isEqualTo(new BigDecimal("8000"));
        assertThat(result.get("holdAmount")).isEqualTo(new BigDecimal("2000"));
        assertThat(result.get("currency")).isEqualTo("GBP");
        assertThat(result.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void getAccountBalance_notFound_returnsError() {
        when(accountService.getBalance("GHOST")).thenThrow(new AccountNotFoundException("GHOST"));

        Map<String, Object> result = accountMcpTool.getAccountBalance("GHOST");

        assertThat(result).containsKey("error");
    }

    // ── get_customer_accounts ─────────────────────────────────────────────────

    @Test
    void getCustomerAccounts_returnsAllAccounts() {
        List<AccountSummary> summaries = List.of(
            new AccountSummary("ACC-001", "GB29BANK0001", "SAVINGS", "ACTIVE",
                    new BigDecimal("10000"), "GBP"),
            new AccountSummary("ACC-002", "GB29BANK0002", "CURRENT", "ACTIVE",
                    new BigDecimal("5000"), "GBP")
        );
        when(accountService.getCustomerAccounts("CUST-001")).thenReturn(summaries);

        Map<String, Object> result = accountMcpTool.getCustomerAccounts("CUST-001");

        assertThat(result.get("customerId")).isEqualTo("CUST-001");
        assertThat(result.get("totalAccounts")).isEqualTo(2);
        assertThat(result).containsKey("accounts");
        List<?> accounts = (List<?>) result.get("accounts");
        assertThat(accounts).hasSize(2);
    }

    @Test
    void getCustomerAccounts_noAccounts_returnsEmptyList() {
        when(accountService.getCustomerAccounts("CUST-NEW")).thenReturn(List.of());

        Map<String, Object> result = accountMcpTool.getCustomerAccounts("CUST-NEW");

        assertThat(result.get("totalAccounts")).isEqualTo(0);
        assertThat((List<?>) result.get("accounts")).isEmpty();
    }

    // ── check_sufficient_funds ────────────────────────────────────────────────

    @Test
    void checkSufficientFunds_hasFunds_returnsTrueWithNoShortfall() {
        when(accountService.getBalance("ACC-001"))
                .thenReturn(balanceResp("ACC-001", new BigDecimal("10000"), new BigDecimal("8000")));
        when(accountService.hasSufficientFunds("ACC-001", new BigDecimal("5000.0")))
                .thenReturn(true);

        Map<String, Object> result = accountMcpTool.checkSufficientFunds("ACC-001", 5000.0);

        assertThat(result.get("hasSufficientFunds")).isEqualTo(true);
        assertThat(result.get("shortfall")).isEqualTo(0);
        assertThat(result.get("requestedAmount")).isEqualTo(5000.0);
    }

    @Test
    void checkSufficientFunds_insufficientFunds_returnsFalseWithShortfall() {
        when(accountService.getBalance("ACC-001"))
                .thenReturn(balanceResp("ACC-001", new BigDecimal("10000"), new BigDecimal("3000")));
        when(accountService.hasSufficientFunds("ACC-001", new BigDecimal("5000.0")))
                .thenReturn(false);

        Map<String, Object> result = accountMcpTool.checkSufficientFunds("ACC-001", 5000.0);

        assertThat(result.get("hasSufficientFunds")).isEqualTo(false);
        assertThat((BigDecimal) result.get("shortfall"))
                .isEqualByComparingTo(new BigDecimal("2000.0")); // 5000 - 3000
    }

    @Test
    void checkSufficientFunds_accountNotFound_returnsError() {
        when(accountService.getBalance("GHOST")).thenThrow(new AccountNotFoundException("GHOST"));

        Map<String, Object> result = accountMcpTool.checkSufficientFunds("GHOST", 500.0);

        assertThat(result).containsKey("error");
    }

    // ── block_account ─────────────────────────────────────────────────────────

    @Test
    void blockAccount_validAccount_returnsBlockedStatus() {
        when(accountService.blockAccount("ACC-001", "Fraud detected"))
                .thenReturn(accountResp("ACC-001", "BLOCKED"));

        Map<String, Object> result = accountMcpTool.blockAccount("ACC-001", "Fraud detected");

        assertThat(result).doesNotContainKey("error");
        assertThat(result.get("accountId")).isEqualTo("ACC-001");
        assertThat(result.get("status")).isEqualTo("BLOCKED");
        assertThat(result.get("reason")).isEqualTo("Fraud detected");
        assertThat(result.get("message").toString()).contains("Human review required");
    }

    @Test
    void blockAccount_notFound_returnsError() {
        when(accountService.blockAccount("GHOST", "Fraud"))
                .thenThrow(new AccountNotFoundException("GHOST"));

        Map<String, Object> result = accountMcpTool.blockAccount("GHOST", "Fraud");

        assertThat(result).containsKey("error");
    }

    @Test
    void blockAccount_logsWarning() {
        when(accountService.blockAccount("ACC-001", "Suspicious activity"))
                .thenReturn(accountResp("ACC-001", "BLOCKED"));

        // verify the service was called with exact arguments (audit trail)
        accountMcpTool.blockAccount("ACC-001", "Suspicious activity");

        verify(accountService).blockAccount("ACC-001", "Suspicious activity");
    }
}