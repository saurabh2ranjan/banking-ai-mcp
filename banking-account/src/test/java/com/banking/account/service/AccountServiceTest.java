package com.banking.account.service;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.notification.service.NotificationService;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.service.CustomerOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock AccountRepository          accountRepository;
    @Mock AccountMapper              accountMapper;
    @Mock CustomerOnboardingService  onboardingService;
    @Mock NotificationService        notificationService;

    @InjectMocks AccountService accountService;

    private Customer verifiedCustomer;
    private Account  activeAccount;

    @BeforeEach
    void setUp() {
        verifiedCustomer = Customer.builder()
                .customerId("CUST-001")
                .firstName("Alice").lastName("Johnson")
                .email("alice@example.com")
                .kycStatus(Customer.KycStatus.VERIFIED)
                .onboardingStatus(Customer.OnboardingStatus.COMPLETED)
                .build();

        activeAccount = Account.builder()
                .accountId("ACC-001").accountNumber("GB29BANK0001")
                .customerId("CUST-001")
                .accountType(Account.AccountType.SAVINGS)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .holdAmount(BigDecimal.ZERO)
                .minimumBalance(new BigDecimal("500.00"))
                .currency("GBP")
                .build();
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private AccountResponse dummyResponse() {
        return new AccountResponse("ACC-001", "GB001", "CUST-001", "My Savings",
                "SAVINGS", "ACTIVE", new BigDecimal("5000"), new BigDecimal("5000"),
                BigDecimal.ZERO, "GBP", new BigDecimal("10000"), new BigDecimal("5000"),
                new BigDecimal("500"), new BigDecimal("0.035"),
                LocalDate.now(), null, LocalDateTime.now(), LocalDateTime.now());
    }

    private BalanceResponse dummyBalance() {
        return new BalanceResponse("ACC-001", "GB29BANK0001",
                new BigDecimal("10000"), new BigDecimal("10000"),
                BigDecimal.ZERO, "GBP", "ACTIVE");
    }

    // ─── openAccount ──────────────────────────────────────────────────────────

    @Nested @DisplayName("openAccount")
    class OpenAccount {

        @Test
        void verifiedCustomer_savesAndNotifies() {
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);
            ArgumentCaptor<Account> cap = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.openAccount(new OpenAccountRequest(
                "CUST-001", Account.AccountType.SAVINGS, "GBP",
                "My Savings", new BigDecimal("5000"), null, null));

            assertThat(cap.getValue().getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
            assertThat(cap.getValue().getBalance()).isEqualByComparingTo("5000");
            assertThat(cap.getValue().getAccountId()).matches("ACC-\\d{6}-\\d{6}-\\d");
            verify(notificationService).sendAccountOpenedNotification(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void kycNotVerified_throwsOnboardingException() {
            verifiedCustomer.setKycStatus(Customer.KycStatus.PENDING);
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);

            assertThatThrownBy(() -> accountService.openAccount(
                new OpenAccountRequest("CUST-001", Account.AccountType.SAVINGS, "GBP", null, null, null, null)))
                    .isInstanceOf(OnboardingException.class)
                    .hasMessageContaining("KYC is not verified");
            verify(accountRepository, never()).save(any());
        }

        @Test
        void onboardingNotComplete_throwsOnboardingException() {
            verifiedCustomer.setOnboardingStatus(Customer.OnboardingStatus.KYC_VERIFIED);
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);

            assertThatThrownBy(() -> accountService.openAccount(
                new OpenAccountRequest("CUST-001", Account.AccountType.SAVINGS, "GBP", null, null, null, null)))
                    .isInstanceOf(OnboardingException.class)
                    .hasMessageContaining("onboarding is not complete");
        }

        @Test
        void nullInitialDeposit_defaultsToZero() {
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);
            ArgumentCaptor<Account> cap = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.openAccount(new OpenAccountRequest(
                "CUST-001", Account.AccountType.CURRENT, "GBP", null, null, null, null));

            assertThat(cap.getValue().getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        void savingsAccount_getsCorrectDefaultsAndInterestRate() {
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);
            ArgumentCaptor<Account> cap = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.openAccount(new OpenAccountRequest(
                "CUST-001", Account.AccountType.SAVINGS, "GBP", null, BigDecimal.ZERO, null, null));

            Account saved = cap.getValue();
            assertThat(saved.getInterestRate()).isEqualByComparingTo("0.0350");
            assertThat(saved.getMinimumBalance()).isEqualByComparingTo("100");
            assertThat(saved.getDailyDebitLimit()).isEqualByComparingTo("10000");
        }

        @Test
        void currentAccount_getsHigherLimitsAndZeroInterest() {
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);
            ArgumentCaptor<Account> cap = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.openAccount(new OpenAccountRequest(
                "CUST-001", Account.AccountType.CURRENT, "GBP", null, BigDecimal.ZERO, null, null));

            Account saved = cap.getValue();
            assertThat(saved.getInterestRate()).isEqualByComparingTo("0.0000");
            assertThat(saved.getDailyDebitLimit()).isEqualByComparingTo("100000");
        }

        @Test
        void customDisplayName_isSaved() {
            when(onboardingService.getCustomerEntity("CUST-001")).thenReturn(verifiedCustomer);
            ArgumentCaptor<Account> cap = ArgumentCaptor.forClass(Account.class);
            when(accountRepository.save(cap.capture())).thenAnswer(i -> i.getArgument(0));
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.openAccount(new OpenAccountRequest(
                "CUST-001", Account.AccountType.SAVINGS, "GBP", "Holiday Fund", BigDecimal.ZERO, null, null));

            assertThat(cap.getValue().getDisplayName()).isEqualTo("Holiday Fund");
        }
    }

    // ─── debitAccount / creditAccount ─────────────────────────────────────────

    @Nested @DisplayName("debitAccount / creditAccount")
    class DebitCredit {

        @Test
        void debit_savesUpdatedAccount() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            accountService.debitAccount("ACC-001", new BigDecimal("3000.00"));

            assertThat(activeAccount.getBalance()).isEqualByComparingTo("7000.00");
            verify(accountRepository).save(activeAccount);
        }

        @Test
        void debit_accountNotFound_throwsNotFoundException() {
            when(accountRepository.findById("GHOST")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> accountService.debitAccount("GHOST", BigDecimal.TEN))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void debit_blockedAccount_throwsAccountInactive() {
            activeAccount.setStatus(Account.AccountStatus.BLOCKED);
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            assertThatThrownBy(() -> accountService.debitAccount("ACC-001", BigDecimal.TEN))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test
        void credit_savesUpdatedAccount() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            accountService.creditAccount("ACC-001", new BigDecimal("500.00"));

            assertThat(activeAccount.getBalance()).isEqualByComparingTo("10500.00");
        }
    }

    // ─── placeHold / releaseHold ──────────────────────────────────────────────

    @Nested @DisplayName("placeHold / releaseHold")
    class Holds {

        @Test
        void placeHold_reducesAvailableBalance() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            accountService.placeHold("ACC-001", new BigDecimal("2000.00"));

            assertThat(activeAccount.getAvailableBalance()).isEqualByComparingTo("8000.00");
            assertThat(activeAccount.getBalance()).isEqualByComparingTo("10000.00"); // unchanged
        }

        @Test
        void releaseHold_restoresAvailableBalance() {
            activeAccount.setHoldAmount(new BigDecimal("2000.00"));
            activeAccount.setAvailableBalance(new BigDecimal("8000.00"));
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            accountService.releaseHold("ACC-001", new BigDecimal("2000.00"));

            assertThat(activeAccount.getAvailableBalance()).isEqualByComparingTo("10000.00");
        }
    }

    // ─── blockAccount / unblockAccount ────────────────────────────────────────

    @Nested @DisplayName("blockAccount / unblockAccount")
    class BlockUnblock {

        @Test
        void blockAccount_setsStatusBlocked() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.blockAccount("ACC-001", "Fraud detected");

            assertThat(activeAccount.getStatus()).isEqualTo(Account.AccountStatus.BLOCKED);
        }

        @Test
        void unblockAccount_setsStatusActive() {
            activeAccount.setStatus(Account.AccountStatus.BLOCKED);
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);
            when(accountMapper.toResponse(any())).thenReturn(dummyResponse());

            accountService.unblockAccount("ACC-001");

            assertThat(activeAccount.getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
        }
    }

    // ─── validateAccountActive ────────────────────────────────────────────────

    @Nested @DisplayName("validateAccountActive / hasSufficientFunds")
    class Queries {

        @Test void activeAccount_returnsTrue() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            assertThat(accountService.validateAccountActive("ACC-001")).isTrue();
        }

        @Test void blockedAccount_returnsFalse() {
            activeAccount.setStatus(Account.AccountStatus.BLOCKED);
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            assertThat(accountService.validateAccountActive("ACC-001")).isFalse();
        }

        @Test void missingAccount_returnsFalse() {
            when(accountRepository.findById("GHOST")).thenReturn(Optional.empty());
            assertThat(accountService.validateAccountActive("GHOST")).isFalse();
        }

        @Test void hasSufficientFunds_true_whenEnoughAvailable() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            assertThat(accountService.hasSufficientFunds("ACC-001", new BigDecimal("5000"))).isTrue();
        }

        @Test void hasSufficientFunds_false_whenInsufficient() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            assertThat(accountService.hasSufficientFunds("ACC-001", new BigDecimal("50000"))).isFalse();
        }

        @Test void getCustomerAccounts_returnsAll() {
            AccountSummary summary = new AccountSummary("ACC-001", "GB001", "SAVINGS", "ACTIVE",
                    new BigDecimal("10000"), "GBP");
            when(accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-001"))
                    .thenReturn(List.of(activeAccount));
            when(accountMapper.toSummary(activeAccount)).thenReturn(summary);

            List<AccountSummary> result = accountService.getCustomerAccounts("CUST-001");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).accountId()).isEqualTo("ACC-001");
        }

        @Test void getCustomerAccounts_noAccounts_returnsEmptyList() {
            when(accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-NONE"))
                    .thenReturn(List.of());
            assertThat(accountService.getCustomerAccounts("CUST-NONE")).isEmpty();
        }

        @Test void getBalance_returnsBalanceResponse() {
            when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
            when(accountMapper.toBalanceResponse(activeAccount)).thenReturn(dummyBalance());
            BalanceResponse bal = accountService.getBalance("ACC-001");
            assertThat(bal.accountId()).isEqualTo("ACC-001");
        }
    }
}
