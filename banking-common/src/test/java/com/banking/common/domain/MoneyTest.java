package com.banking.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    // ── construction ───────────────────────────────────────────────────────────

    @Test
    void of_fromDouble_createsCorrectMoney() {
        Money m = Money.of(100.0, "USD");
        assertThat(m.getAmount()).isEqualByComparingTo("100.00");
        assertThat(m.getCurrency()).isEqualTo("USD");
    }

    @Test
    void of_fromBigDecimal_createsCorrectMoney() {
        Money m = Money.of(new BigDecimal("250.75"), "GBP");
        assertThat(m.getAmount()).isEqualByComparingTo("250.75");
        assertThat(m.getCurrency()).isEqualTo("GBP");
    }

    @Test
    void zero_createsMoneyWithZeroAmount() {
        Money m = Money.zero("EUR");
        assertThat(m.isZero()).isTrue();
        assertThat(m.getCurrency()).isEqualTo("EUR");
    }

    // ── arithmetic ─────────────────────────────────────────────────────────────

    @Test
    void add_sameCurrency_returnsCorrectSum() {
        Money a = Money.of(100.0, "GBP");
        Money b = Money.of(50.0, "GBP");
        assertThat(a.add(b).getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void add_differentCurrency_throwsException() {
        Money gbp = Money.of(100.0, "GBP");
        Money usd = Money.of(50.0, "USD");
        assertThatThrownBy(() -> gbp.add(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtract_sameCurrency_returnsCorrectDifference() {
        Money a = Money.of(200.0, "GBP");
        Money b = Money.of(75.0, "GBP");
        assertThat(a.subtract(b).getAmount()).isEqualByComparingTo("125.00");
    }

    @Test
    void multiply_byPositiveFactor_returnsCorrectProduct() {
        Money m = Money.of(100.0, "GBP");
        assertThat(m.multiply(3).getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void multiply_byZero_returnsZeroMoney() {
        Money m = Money.of(100.0, "GBP");
        assertThat(m.multiply(0).isZero()).isTrue();
    }

    // ── comparisons ────────────────────────────────────────────────────────────

    @Test
    void isGreaterThan_returnsTrue_whenAmountIsHigher() {
        Money big   = Money.of(200.0, "GBP");
        Money small = Money.of(100.0, "GBP");
        assertThat(big.isGreaterThan(small)).isTrue();
    }

    @Test
    void isGreaterThan_returnsFalse_whenEqual() {
        Money a = Money.of(100.0, "GBP");
        Money b = Money.of(100.0, "GBP");
        assertThat(a.isGreaterThan(b)).isFalse();
    }

    @Test
    void isLessThan_returnsTrue_whenAmountIsLower() {
        Money small = Money.of(50.0, "GBP");
        Money big   = Money.of(100.0, "GBP");
        assertThat(small.isLessThan(big)).isTrue();
    }

    @Test
    void isZero_returnsTrue_forZeroAmount() {
        assertThat(Money.zero("GBP").isZero()).isTrue();
    }

    @Test
    void isZero_returnsFalse_forPositiveAmount() {
        assertThat(Money.of(0.01, "GBP").isZero()).isFalse();
    }

    // ── equality ───────────────────────────────────────────────────────────────

    @Test
    void equals_sameCurrencyAndAmount_returnsTrue() {
        Money a = Money.of(new BigDecimal("100.00"), "GBP");
        Money b = Money.of(new BigDecimal("100.0"),  "GBP");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentCurrency_returnsFalse() {
        Money gbp = Money.of(100.0, "GBP");
        Money usd = Money.of(100.0, "USD");
        assertThat(gbp).isNotEqualTo(usd);
    }

    @Test
    void equals_differentAmount_returnsFalse() {
        assertThat(Money.of(100.0, "GBP")).isNotEqualTo(Money.of(200.0, "GBP"));
    }

    @Test
    void hashCode_equalMoneys_haveSameHashCode() {
        Money a = Money.of(new BigDecimal("100.00"), "GBP");
        Money b = Money.of(new BigDecimal("100.0"),  "GBP");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── toString ───────────────────────────────────────────────────────────────

    @Test
    void toString_includesCurrencyAndAmount() {
        assertThat(Money.of(99.5, "USD").toString()).contains("USD").contains("99.50");
    }

    // ── immutability ───────────────────────────────────────────────────────────

    @Test
    void add_doesNotMutateOriginal() {
        Money original = Money.of(100.0, "GBP");
        original.add(Money.of(50.0, "GBP"));
        assertThat(original.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void subtract_doesNotMutateOriginal() {
        Money original = Money.of(100.0, "GBP");
        original.subtract(Money.of(30.0, "GBP"));
        assertThat(original.getAmount()).isEqualByComparingTo("100.00");
    }

    // ── currency mismatch ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"subtract", "isGreaterThan", "isLessThan"})
    void currencyMismatch_throwsIllegalArgument(String operation) {
        Money gbp = Money.of(100.0, "GBP");
        Money usd = Money.of(50.0, "USD");
        switch (operation) {
            case "subtract"      -> assertThatThrownBy(() -> gbp.subtract(usd))
                                         .isInstanceOf(IllegalArgumentException.class);
            case "isGreaterThan" -> assertThatThrownBy(() -> gbp.isGreaterThan(usd))
                                         .isInstanceOf(IllegalArgumentException.class);
            case "isLessThan"    -> assertThatThrownBy(() -> gbp.isLessThan(usd))
                                         .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
