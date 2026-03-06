package com.banking.account.domain;

import com.banking.common.exception.BankingExceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Account — domain entity")
class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountId("ACC-001")
                .accountNumber("GB29BANK0001")
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

    // ─── debit() ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("debit()")
    class Debit {

        @Test void reducesBalanceAndAvailable() {
            account.debit(new BigDecimal("3000.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("7000.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("7000.00");
        }

        @Test void exactMaximumDebit_leaves500MinBalance() {
            account.debit(new BigDecimal("9500.00"));          // 10000 - 9500 = 500 (minimum)
            assertThat(account.getBalance()).isEqualByComparingTo("500.00");
        }

        @Test void wouldBreachMinimumBalance_throwsInsufficientFunds() {
            // leaving 400 < 500 minimum
            assertThatThrownBy(() -> account.debit(new BigDecimal("9600.00")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("minimum balance");
        }

        @Test void exceedsAvailableBalance_throwsInsufficientFunds() {
            assertThatThrownBy(() -> account.debit(new BigDecimal("15000.00")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("ACC-001");
        }

        @Test void exceedsSingleTransactionLimit_throwsPaymentException() {
            account.setSingleTransactionLimit(new BigDecimal("1000.00"));
            assertThatThrownBy(() -> account.debit(new BigDecimal("1001.00")))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("exceeds single transaction limit");
        }

        @Test void atSingleTransactionLimit_succeeds() {
            account.setSingleTransactionLimit(new BigDecimal("1000.00"));
            assertThatCode(() -> account.debit(new BigDecimal("1000.00"))).doesNotThrowAnyException();
        }

        @Test void nullSingleLimit_skipsLimitCheck() {
            account.setSingleTransactionLimit(null);
            assertThatCode(() -> account.debit(new BigDecimal("5000.00"))).doesNotThrowAnyException();
        }

        @Test void blockedAccount_throwsAccountInactive() {
            account.setStatus(Account.AccountStatus.BLOCKED);
            assertThatThrownBy(() -> account.debit(BigDecimal.ONE))
                    .isInstanceOf(AccountInactiveException.class);
        }

        @Test void closedAccount_throwsAccountInactive() {
            account.setStatus(Account.AccountStatus.CLOSED);
            assertThatThrownBy(() -> account.debit(BigDecimal.ONE))
                    .isInstanceOf(AccountInactiveException.class);
        }
    }

    // ─── credit() ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("credit()")
    class Credit {

        @Test void increasesBalanceAndAvailable() {
            account.credit(new BigDecimal("2500.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("12500.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("12500.00");
        }

        @Test void multipleCredits_accumulate() {
            account.credit(new BigDecimal("1000.00"));
            account.credit(new BigDecimal("2000.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("13000.00");
        }

        @Test void blockedAccount_throwsAccountInactive() {
            account.setStatus(Account.AccountStatus.BLOCKED);
            assertThatThrownBy(() -> account.credit(BigDecimal.ONE))
                    .isInstanceOf(AccountInactiveException.class);
        }
    }

    // ─── placeHold() / releaseHold() ──────────────────────────────────────────

    @Nested @DisplayName("placeHold / releaseHold")
    class Holds {

        @Test void placeHold_reducesAvailableNotBalance() {
            account.placeHold(new BigDecimal("2000.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("10000.00");          // unchanged
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000.00");  // reduced
            assertThat(account.getHoldAmount()).isEqualByComparingTo("2000.00");
        }

        @Test void releaseHold_restoresAvailableBalance() {
            account.placeHold(new BigDecimal("2000.00"));
            account.releaseHold(new BigDecimal("2000.00"));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
            assertThat(account.getHoldAmount()).isEqualByComparingTo("0.00");
        }

        @Test void placeHold_exceedingAvailable_throwsInsufficientFunds() {
            assertThatThrownBy(() -> account.placeHold(new BigDecimal("15000.00")))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test void multipleHolds_accumulate() {
            account.placeHold(new BigDecimal("1000.00"));
            account.placeHold(new BigDecimal("2000.00"));
            assertThat(account.getHoldAmount()).isEqualByComparingTo("3000.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("7000.00");
        }

        @Test void partialRelease_reducesHoldCorrectly() {
            account.placeHold(new BigDecimal("5000.00"));
            account.releaseHold(new BigDecimal("2000.00"));
            assertThat(account.getHoldAmount()).isEqualByComparingTo("3000.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("7000.00");
        }

        @Test void releaseMoreThanHeld_holdsFloorAtZero() {
            account.placeHold(new BigDecimal("1000.00"));
            account.releaseHold(new BigDecimal("5000.00"));             // release more than held
            assertThat(account.getHoldAmount()).isEqualByComparingTo("0.00");
        }
    }

    // ─── Status helpers ───────────────────────────────────────────────────────

    @Nested @DisplayName("isActive / hasSufficientFunds")
    class StatusHelpers {

        @Test void isActive_true_forActiveAccount() {
            assertThat(account.isActive()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Account.AccountStatus.class, names = {"BLOCKED", "DORMANT", "CLOSED", "INACTIVE"})
        void isActive_false_forNonActiveStatuses(Account.AccountStatus status) {
            account.setStatus(status);
            assertThat(account.isActive()).isFalse();
        }

        @Test void hasSufficientFunds_true_whenEnoughAvailable() {
            assertThat(account.hasSufficientFunds(new BigDecimal("5000.00"))).isTrue();
        }

        @Test void hasSufficientFunds_true_forExactAmount() {
            assertThat(account.hasSufficientFunds(new BigDecimal("10000.00"))).isTrue();
        }

        @Test void hasSufficientFunds_false_whenInsufficient() {
            assertThat(account.hasSufficientFunds(new BigDecimal("20000.00"))).isFalse();
        }

        @Test void hasSufficientFunds_accountsForExistingHolds() {
            account.placeHold(new BigDecimal("8000.00"));          // only 2000 available
            assertThat(account.hasSufficientFunds(new BigDecimal("3000.00"))).isFalse();
            assertThat(account.hasSufficientFunds(new BigDecimal("2000.00"))).isTrue();
        }
    }

    // ─── Complete payment lifecycle ────────────────────────────────────────────

    @Nested @DisplayName("Full payment lifecycle (hold → release → debit → credit)")
    class PaymentLifecycle {

        @Test void holdThenDebitThenCredit_maintainsCorrectState() {
            // 1. Place hold
            account.placeHold(new BigDecimal("3000.00"));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("7000.00");

            // 2. Fraud check passes → release hold and debit
            account.releaseHold(new BigDecimal("3000.00"));
            account.debit(new BigDecimal("3000.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("7000.00");
            assertThat(account.getHoldAmount()).isEqualByComparingTo("0.00");

            // 3. Credit destination
            Account dest = Account.builder()
                    .accountId("ACC-002").status(Account.AccountStatus.ACTIVE)
                    .balance(BigDecimal.ZERO).availableBalance(BigDecimal.ZERO)
                    .holdAmount(BigDecimal.ZERO).minimumBalance(BigDecimal.ZERO)
                    .currency("GBP").build();
            dest.credit(new BigDecimal("3000.00"));
            assertThat(dest.getBalance()).isEqualByComparingTo("3000.00");
        }

        @Test void fraudFail_releaseHoldRestoresFunds() {
            account.placeHold(new BigDecimal("5000.00"));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("5000.00");

            // Fraud detected → release hold without debiting
            account.releaseHold(new BigDecimal("5000.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("10000.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
        }
    }
}
