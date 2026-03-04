package com.banking.account.domain;

import com.banking.common.domain.BaseEntity;
import com.banking.common.exception.BankingExceptions.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bank Account entity.
 * Linked to a Customer (by ID reference — not JPA FK to keep modules loosely coupled).
 * All monetary operations are atomic and validated before execution.
 */
@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_account_customer",    columnList = "customer_id"),
        @Index(name = "idx_account_status",      columnList = "status"),
        @Index(name = "idx_account_type_status", columnList = "account_type, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Id
    @Column(name = "account_id", length = 30)
    private String accountId;

    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;        // Cross-module ref (not JPA FK)

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;     // IBAN/BBAN formatted

    @Column(name = "display_name", length = 100)
    private String displayName;       // Friendly label set by customer

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;   // Balance minus holds

    @Column(name = "hold_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal holdAmount = BigDecimal.ZERO;

    // ─── Limits ──────────────────────────────────────────────────────────
    @Column(name = "daily_debit_limit",  precision = 19, scale = 2)
    private BigDecimal dailyDebitLimit;

    @Column(name = "single_txn_limit",   precision = 19, scale = 2)
    private BigDecimal singleTransactionLimit;

    @Column(name = "minimum_balance",    precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    // ─── Interest ─────────────────────────────────────────────────────────
    @Column(name = "interest_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal interestRate = BigDecimal.ZERO;

    // ─── Dates ────────────────────────────────────────────────────────────
    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;     // For fixed deposits

    // ─── Domain Methods ───────────────────────────────────────────────────

    public void debit(BigDecimal amount) {
        validateActiveForTransaction();
        validateSingleTransactionLimit(amount);
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId);
        }
        BigDecimal newBalance = balance.subtract(amount);
        if (newBalance.compareTo(minimumBalance) < 0) {
            throw new InsufficientFundsException(accountId + " (would breach minimum balance)");
        }
        this.balance          = newBalance;
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        validateActiveForTransaction();
        this.balance          = this.balance.add(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void placeHold(BigDecimal amount) {
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId);
        }
        this.holdAmount       = this.holdAmount.add(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public void releaseHold(BigDecimal amount) {
        this.holdAmount       = this.holdAmount.subtract(amount).max(BigDecimal.ZERO);
        this.availableBalance = this.availableBalance.add(amount);
    }

    public boolean isActive()             { return AccountStatus.ACTIVE.equals(status); }
    public boolean hasSufficientFunds(BigDecimal amount) { return availableBalance.compareTo(amount) >= 0; }

    private void validateActiveForTransaction() {
        if (!isActive()) throw new AccountInactiveException(accountId);
    }

    private void validateSingleTransactionLimit(BigDecimal amount) {
        if (singleTransactionLimit != null && amount.compareTo(singleTransactionLimit) > 0) {
            throw new PaymentException(
                "Amount " + amount + " exceeds single transaction limit " + singleTransactionLimit,
                "SINGLE_TXN_LIMIT_EXCEEDED"
            );
        }
    }

    public enum AccountType   { SAVINGS, CURRENT, FIXED_DEPOSIT, SALARY, NRI }
    public enum AccountStatus { ACTIVE, INACTIVE, BLOCKED, DORMANT, CLOSED }
}
