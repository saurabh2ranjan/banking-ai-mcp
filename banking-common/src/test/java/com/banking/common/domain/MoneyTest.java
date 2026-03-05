package com.banking.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money — value object")
class MoneyTest {

    // ─── Construction ─────────────────────────────────────────────────────────

    @Nested @DisplayName("Construction")
    class Construction {

        @Test
        void of_double_createsCorrectMoney() {
            Money m = Money.of(100.0, "USD");
            assertThat(m.getAmount()).isEqualByComparingTo("100.00");
            assertThat(m.getCurrency()).isEqualTo("USD");
        }

        @Test
        void of_bigDecimal_createsCorrectMoney() {
            Money m = Money.of(new BigDecimal("250.75"), "GBP");
            assertThat(m.getAmount()).isEqualByComparingTo("250.75");
            assertThat(m.getCurrency()).isEqualTo("GBP");
        }

        @Test
        void zero_createsZeroMoney() {
            Money m = Money.zero("EUR");
            assertThat(m.isZero()).isTrue();
            assertThat(m.getCurrency()).isEqualTo("EUR");
        }

        @Test
        void currency_normalisedToUpperCase() {
            assertThat(Money.of(10.0, "usd").getCurrency()).isEqualTo("USD");
            assertThat(Money.of(10.0, "gbp").getCurrency()).isEqualTo("GBP");
        }

        @Test
        void amount_roundedHalfUpToTwoDecimals() {
            // 10.555 rounds to 10.56
            assertThat(Money.of(new BigDecimal("10.555"), "USD").getAmount())
                    .isEqualByComparingTo("10.56");
            // 10.554 rounds to 10.55
            assertThat(Money.of(new BigDecimal("10.554"), "USD").getAmount())
                    .isEqualByComparingTo("10.55");
        }

        @Test
        void negativeAmount_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> Money.of(-0.01, "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void nullAmount_throwsNullPointerException() {
            assertThatThrownBy(() -> new Money(null, "USD"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void nullCurrency_throwsNullPointerException() {
            assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"INVALID", "XX", "12345", ""})
        void invalidCurrencyCode_throwsException(String bad) {
            assertThatThrownBy(() -> Money.of(1.0, bad)).isInstanceOf(Exception.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"USD", "GBP", "EUR", "INR", "JPY", "CHF", "SGD"})
        void validIsoCurrencies_accepted(String ccy) {
            assertThatCode(() -> Money.of(1.0, ccy)).doesNotThrowAnyException();
        }
    }

    // ─── Arithmetic ───────────────────────────────────────────────────────────

    @Nested @DisplayName("Arithmetic")
    class Arithmetic {

        @Test
        void add_sameCurrency_returnsSum() {
            assertThat(Money.of(100.0, "USD").add(Money.of(50.0, "USD")))
                    .isEqualTo(Money.of(150.0, "USD"));
        }

        @Test
        void add_differentCurrencies_throwsMismatch() {
            assertThatThrownBy(() -> Money.of(100.0, "USD").add(Money.of(100.0, "GBP")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        void subtract_sameCurrency_returnsDifference() {
            assertThat(Money.of(100.0, "USD").subtract(Money.of(30.0, "USD")))
                    .isEqualTo(Money.of(70.0, "USD"));
        }

        @Test
        void multiply_byPositiveFactor_returnsProduct() {
            assertThat(Money.of(25.0, "GBP").multiply(4))
                    .isEqualTo(Money.of(100.0, "GBP"));
        }

        @Test
        void multiply_byZero_returnsZeroMoney() {
            assertThat(Money.of(500.0, "USD").multiply(0).isZero()).isTrue();
        }

        @Test
        void arithmetic_doesNotMutateOriginal() {
            Money original = Money.of(100.0, "USD");
            original.add(Money.of(50.0, "USD"));
            original.subtract(Money.of(30.0, "USD"));
            original.multiply(3);
            assertThat(original).isEqualTo(Money.of(100.0, "USD"));
        }
    }

    // ─── Comparison ───────────────────────────────────────────────────────────

    @Nested @DisplayName("Comparison")
    class Comparison {

        @Test
        void isGreaterThan_trueWhenLarger() {
            assertThat(Money.of(200.0, "USD").isGreaterThan(Money.of(100.0, "USD"))).isTrue();
        }

        @Test
        void isGreaterThan_falseWhenEqual() {
            assertThat(Money.of(100.0, "USD").isGreaterThan(Money.of(100.0, "USD"))).isFalse();
        }

        @Test
        void isGreaterThan_falseWhenSmaller() {
            assertThat(Money.of(50.0, "USD").isGreaterThan(Money.of(100.0, "USD"))).isFalse();
        }

        @Test
        void isLessThan_trueWhenSmaller() {
            assertThat(Money.of(50.0, "GBP").isLessThan(Money.of(100.0, "GBP"))).isTrue();
        }

        @Test
        void isZero_trueForZero() {
            assertThat(Money.zero("USD").isZero()).isTrue();
        }

        @Test
        void isZero_falseForNonZero() {
            assertThat(Money.of(0.01, "USD").isZero()).isFalse();
        }

        @Test
        void crossCurrencyComparisons_throwMismatch() {
            Money usd = Money.of(100.0, "USD");
            Money gbp = Money.of(100.0, "GBP");
            assertThatThrownBy(() -> usd.isGreaterThan(gbp)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> usd.isLessThan(gbp)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── Equality & Hash ──────────────────────────────────────────────────────

    @Nested @DisplayName("Equality and hashCode")
    class EqualityAndHash {

        @Test
        void equalObjects_withTrailingZeros_areEqual() {
            assertThat(Money.of(new BigDecimal("100.00"), "USD"))
                    .isEqualTo(Money.of(new BigDecimal("100"), "USD"));
        }

        @Test
        void differentCurrency_notEqual() {
            assertThat(Money.of(100.0, "USD")).isNotEqualTo(Money.of(100.0, "GBP"));
        }

        @Test
        void differentAmount_notEqual() {
            assertThat(Money.of(100.0, "USD")).isNotEqualTo(Money.of(200.0, "USD"));
        }

        @Test
        void equalObjects_haveSameHashCode() {
            Money a = Money.of(100.0, "USD");
            Money b = Money.of(new BigDecimal("100.00"), "USD");
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_returnsFormattedString() {
            assertThat(Money.of(1500.50, "GBP").toString()).isEqualTo("GBP 1500.50");
        }
    }

    // ─── Parameterised rounding ───────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"10.004,10.00", "10.005,10.01", "10.995,11.00", "0.001,0.00"})
    @DisplayName("HALF_UP rounding edge cases")
    void halfUpRounding(String input, String expected) {
        assertThat(Money.of(new BigDecimal(input), "USD").getAmount())
                .isEqualByComparingTo(new BigDecimal(expected));
    }
}
