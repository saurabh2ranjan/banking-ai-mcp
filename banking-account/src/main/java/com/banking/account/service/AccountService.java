package com.banking.account.service;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.common.util.AccountNumberGenerator;
import com.banking.notification.service.NotificationService;
import com.banking.onboarding.service.CustomerOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository          accountRepository;
    private final AccountMapper              accountMapper;
    private final CustomerOnboardingService  onboardingService;
    private final NotificationService        notificationService;

    // ─── Account Opening ──────────────────────────────────────────────────

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        log.info("Opening account for customer: {} type: {}", request.customerId(), request.accountType());

        // Validate customer is onboarded and KYC verified
        var customer = onboardingService.getCustomerEntity(request.customerId());
        if (!customer.isKycVerified()) {
            throw new OnboardingException("Account cannot be opened — KYC is not verified for customer: " + request.customerId());
        }
        if (!customer.isOnboardingComplete()) {
            throw new OnboardingException("Account cannot be opened — onboarding is not complete for customer: " + request.customerId());
        }

        String accountId     = AccountNumberGenerator.generate();
        String accountNumber = generateIban(request.currency());

        BigDecimal initial = request.initialDeposit() != null ? request.initialDeposit() : BigDecimal.ZERO;

        Account account = Account.builder()
            .accountId(accountId)
            .accountNumber(accountNumber)
            .customerId(request.customerId())
            .displayName(request.displayName() != null ? request.displayName() : request.accountType().name() + " Account")
            .accountType(request.accountType())
            .status(Account.AccountStatus.ACTIVE)
            .currency(request.currency())
            .balance(initial)
            .availableBalance(initial)
            .holdAmount(BigDecimal.ZERO)
            .dailyDebitLimit(request.dailyDebitLimit() != null ? request.dailyDebitLimit() : defaultDailyLimit(request.accountType()))
            .singleTransactionLimit(request.singleTransactionLimit() != null ? request.singleTransactionLimit() : defaultSingleLimit(request.accountType()))
            .minimumBalance(minimumBalanceFor(request.accountType()))
            .interestRate(interestRateFor(request.accountType()))
            .openedDate(LocalDate.now())
            .build();

        Account saved = accountRepository.save(account);
        log.info("Account opened: {} for customer: {}", saved.getAccountId(), request.customerId());

        notificationService.sendAccountOpenedNotification(
            customer.getEmail(), customer.getFullName(), saved.getAccountId(), request.accountType().name());

        return accountMapper.toResponse(saved);
    }

    // ─── Balance Operations ───────────────────────────────────────────────

    @Transactional
    public void debitAccount(String accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        account.debit(amount);
        accountRepository.save(account);
        log.debug("Debited {} {} from account {}", amount, account.getCurrency(), accountId);
    }

    @Transactional
    public void creditAccount(String accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        account.credit(amount);
        accountRepository.save(account);
        log.debug("Credited {} {} to account {}", amount, account.getCurrency(), accountId);
    }

    @Transactional
    public void placeHold(String accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        account.placeHold(amount);
        accountRepository.save(account);
    }

    @Transactional
    public void releaseHold(String accountId, BigDecimal amount) {
        Account account = getActiveAccount(accountId);
        account.releaseHold(amount);
        accountRepository.save(account);
    }

    // ─── Queries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        return accountMapper.toResponse(findAccount(accountId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        return accountMapper.toBalanceResponse(findAccount(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountSummary> getCustomerAccounts(String customerId) {
        return accountRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                                .stream()
                                .map(accountMapper::toSummary)
                                .toList();
    }

    @Transactional(readOnly = true)
    public boolean validateAccountActive(String accountId) {
        return accountRepository.findById(accountId)
                                .map(Account::isActive)
                                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasSufficientFunds(String accountId, BigDecimal amount) {
        return findAccount(accountId).hasSufficientFunds(amount);
    }

    // ─── Admin Operations ─────────────────────────────────────────────────

    @Transactional
    public AccountResponse blockAccount(String accountId, String reason) {
        Account account = findAccount(accountId);
        account.setStatus(Account.AccountStatus.BLOCKED);
        Account saved = accountRepository.save(account);
        log.warn("Account BLOCKED: {} | Reason: {}", accountId, reason);
        // In production: notify customer + compliance
        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse unblockAccount(String accountId) {
        Account account = findAccount(accountId);
        account.setStatus(Account.AccountStatus.ACTIVE);
        return accountMapper.toResponse(accountRepository.save(account));
    }

    // ─── Internal ─────────────────────────────────────────────────────────

    public Account findAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Account getActiveAccount(String accountId) {
        Account account = findAccount(accountId);
        if (!account.isActive()) throw new AccountInactiveException(accountId);
        return account;
    }

    private String generateIban(String currency) {
        // Simplified IBAN-style — use a proper generator in production
        return "GB" + String.format("%02d", (int)(Math.random()*90)+10)
             + "BANK" + String.format("%014d", (long)(Math.random()*100_000_000_000_000L));
    }

    private BigDecimal defaultDailyLimit(Account.AccountType type) {
        return switch (type) {
            case SAVINGS       -> new BigDecimal("10000");
            case CURRENT       -> new BigDecimal("100000");
            case SALARY        -> new BigDecimal("50000");
            case FIXED_DEPOSIT -> BigDecimal.ZERO;
            case NRI           -> new BigDecimal("200000");
        };
    }

    private BigDecimal defaultSingleLimit(Account.AccountType type) {
        return switch (type) {
            case SAVINGS       -> new BigDecimal("5000");
            case CURRENT       -> new BigDecimal("50000");
            case SALARY        -> new BigDecimal("25000");
            case FIXED_DEPOSIT -> BigDecimal.ZERO;
            case NRI           -> new BigDecimal("100000");
        };
    }

    private BigDecimal minimumBalanceFor(Account.AccountType type) {
        return switch (type) {
            case SAVINGS -> new BigDecimal("500");
            case CURRENT -> new BigDecimal("5000");
            default      -> BigDecimal.ZERO;
        };
    }

    private BigDecimal interestRateFor(Account.AccountType type) {
        return switch (type) {
            case SAVINGS, NRI  -> new BigDecimal("0.0350");
            case FIXED_DEPOSIT -> new BigDecimal("0.0650");
            default            -> BigDecimal.ZERO;
        };
    }
}
