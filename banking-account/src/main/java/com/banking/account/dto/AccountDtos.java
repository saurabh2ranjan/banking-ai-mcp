package com.banking.account.dto;

import com.banking.account.domain.Account;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AccountDtos {

    public record OpenAccountRequest(
        @NotBlank String customerId,
        @NotNull  Account.AccountType accountType,
        @NotBlank @Size(min = 3, max = 3) String currency,
        String displayName,
        BigDecimal initialDeposit,
        BigDecimal dailyDebitLimit,
        BigDecimal singleTransactionLimit
    ) {}

    public record AccountResponse(
        String accountId,
        String accountNumber,
        String customerId,
        String displayName,
        String accountType,
        String status,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal holdAmount,
        String currency,
        BigDecimal dailyDebitLimit,
        BigDecimal singleTransactionLimit,
        BigDecimal minimumBalance,
        BigDecimal interestRate,
        LocalDate openedDate,
        LocalDate maturityDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record AccountSummary(
        String accountId,
        String accountNumber,
        String accountType,
        String status,
        BigDecimal balance,
        String currency
    ) {}

    public record BalanceResponse(
        String accountId,
        String accountNumber,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal holdAmount,
        String currency,
        String status
    ) {}
}
