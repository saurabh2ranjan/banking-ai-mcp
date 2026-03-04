package com.banking.account.mcp;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.BankingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMcpTool {

    private final AccountService accountService;

    @Tool(name = "open_bank_account",
          description = "Open a new bank account for an onboarded customer. " +
                        "Customer must have verified KYC before an account can be opened. " +
                        "Account types: SAVINGS, CURRENT, FIXED_DEPOSIT, SALARY, NRI.")
    public Map<String, Object> openBankAccount(
            @ToolParam(description = "Customer ID who will own the account") String customerId,
            @ToolParam(description = "Account type: SAVINGS, CURRENT, FIXED_DEPOSIT, SALARY, NRI") String accountType,
            @ToolParam(description = "ISO 4217 currency code (e.g. USD, GBP, INR)") String currency,
            @ToolParam(description = "Initial deposit amount") double initialDeposit,
            @ToolParam(description = "Friendly display name for the account") String displayName) {
        try {
            OpenAccountRequest req = new OpenAccountRequest(
                customerId, Account.AccountType.valueOf(accountType.toUpperCase()),
                currency, displayName, BigDecimal.valueOf(initialDeposit),
                null, null
            );
            AccountResponse acc = accountService.openAccount(req);
            return Map.of(
                "accountId",     acc.accountId(),
                "accountNumber", acc.accountNumber(),
                "accountType",   acc.accountType(),
                "balance",       acc.balance(),
                "currency",      acc.currency(),
                "status",        acc.status(),
                "interestRate",  acc.interestRate(),
                "dailyLimit",    acc.dailyDebitLimit(),
                "message",       "Account successfully opened."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid account type. Valid: SAVINGS, CURRENT, FIXED_DEPOSIT, SALARY, NRI");
        }
    }

    @Tool(name = "get_account_details",
          description = "Retrieve full details of a bank account including balance, limits, status, and metadata.")
    public Map<String, Object> getAccountDetails(
            @ToolParam(description = "Account ID to retrieve") String accountId) {
        try {
            AccountResponse acc = accountService.getAccount(accountId);
            return Map.ofEntries(
                Map.entry("accountId",              acc.accountId()),
                Map.entry("accountNumber",          acc.accountNumber()),
                Map.entry("customerId",             acc.customerId()),
                Map.entry("displayName",            acc.displayName() != null ? acc.displayName() : ""),
                Map.entry("accountType",            acc.accountType()),
                Map.entry("status",                 acc.status()),
                Map.entry("balance",                acc.balance()),
                Map.entry("availableBalance",       acc.availableBalance()),
                Map.entry("holdAmount",             acc.holdAmount()),
                Map.entry("currency",               acc.currency()),
                Map.entry("dailyDebitLimit",        acc.dailyDebitLimit() != null ? acc.dailyDebitLimit() : "N/A"),
                Map.entry("singleTransactionLimit", acc.singleTransactionLimit() != null ? acc.singleTransactionLimit() : "N/A"),
                Map.entry("minimumBalance",         acc.minimumBalance()),
                Map.entry("interestRate",           acc.interestRate()),
                Map.entry("openedDate",             acc.openedDate().toString()),
                Map.entry("createdAt",              acc.createdAt().toString())
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage(), "errorCode", e.getErrorCode());
        }
    }

    @Tool(name = "get_account_balance",
          description = "Get the current balance, available balance, and any hold amounts for an account.")
    public Map<String, Object> getAccountBalance(
            @ToolParam(description = "Account ID to check balance") String accountId) {
        try {
            BalanceResponse bal = accountService.getBalance(accountId);
            return Map.of(
                "accountId",       bal.accountId(),
                "accountNumber",   bal.accountNumber(),
                "balance",         bal.balance(),
                "availableBalance",bal.availableBalance(),
                "holdAmount",      bal.holdAmount(),
                "currency",        bal.currency(),
                "status",          bal.status()
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "get_customer_accounts",
          description = "List all accounts (active, inactive, blocked) belonging to a customer.")
    public Map<String, Object> getCustomerAccounts(
            @ToolParam(description = "Customer ID to list accounts for") String customerId) {
        List<AccountSummary> accounts = accountService.getCustomerAccounts(customerId);
        return Map.of(
            "customerId",    customerId,
            "totalAccounts", accounts.size(),
            "accounts", accounts.stream()
                .map(a -> Map.of(
                    "accountId",     a.accountId(),
                    "accountNumber", a.accountNumber(),
                    "accountType",   a.accountType(),
                    "status",        a.status(),
                    "balance",       a.balance(),
                    "currency",      a.currency()
                ))
                .collect(Collectors.toList())
        );
    }

    @Tool(name = "check_sufficient_funds",
          description = "Check whether an account has enough available balance to cover a given amount.")
    public Map<String, Object> checkSufficientFunds(
            @ToolParam(description = "Account ID to check") String accountId,
            @ToolParam(description = "Amount to verify") double amount) {
        try {
            BalanceResponse bal = accountService.getBalance(accountId);
            boolean sufficient = accountService.hasSufficientFunds(accountId, BigDecimal.valueOf(amount));
            return Map.of(
                "accountId",          accountId,
                "requestedAmount",    amount,
                "availableBalance",   bal.availableBalance(),
                "currency",           bal.currency(),
                "hasSufficientFunds", sufficient,
                "shortfall",          sufficient ? 0 : BigDecimal.valueOf(amount).subtract(bal.availableBalance())
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "block_account",
          description = "Block a bank account immediately. Use when fraud or suspicious activity is confirmed. " +
                        "Customer will not be able to transact until unblocked by compliance.")
    public Map<String, Object> blockAccount(
            @ToolParam(description = "Account ID to block") String accountId,
            @ToolParam(description = "Reason for blocking the account (mandatory for audit trail)") String reason) {
        try {
            log.warn("🚨 AI-TRIGGERED ACCOUNT BLOCK: accountId={}, reason={}", accountId, reason);
            AccountResponse acc = accountService.blockAccount(accountId, reason);
            return Map.of(
                "accountId", acc.accountId(),
                "status",    acc.status(),
                "reason",    reason,
                "message",   "Account blocked. Compliance team notified. Human review required to unblock."
            );
        } catch (BankingException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
