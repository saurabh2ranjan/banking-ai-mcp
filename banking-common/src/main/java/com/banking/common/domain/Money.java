package com.banking.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable Money value object using ISO 4217 currency codes.
 * Embedded into JPA entities to avoid primitive monetary fields.
 */
@Embeddable
public final class Money {

    private final BigDecimal amount;
    private final String currency;

    protected Money() {
        // JPA
        this.amount = BigDecimal.ZERO;
        this.currency = "USD";
    }

    @JsonCreator
    public Money(@JsonProperty("amount") BigDecimal amount,
                 @JsonProperty("currency") String currency) {
        Objects.requireNonNull(amount,   "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        // Validate ISO 4217
        Currency.getInstance(currency.toUpperCase());
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
        this.amount   = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency.toUpperCase();
    }

    public static Money of(double amount,     String currency) { return new Money(BigDecimal.valueOf(amount), currency); }
    public static Money of(BigDecimal amount, String currency) { return new Money(amount, currency); }
    public static Money zero(String currency)                  { return new Money(BigDecimal.ZERO, currency); }

    public Money add(Money other)      { assertSameCurrency(other); return new Money(amount.add(other.amount), currency); }
    public Money subtract(Money other) { assertSameCurrency(other); return new Money(amount.subtract(other.amount), currency); }
    public Money multiply(int factor)  { return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency); }

    public boolean isGreaterThan(Money other) { assertSameCurrency(other); return amount.compareTo(other.amount) > 0; }
    public boolean isLessThan(Money other)    { assertSameCurrency(other); return amount.compareTo(other.amount) < 0; }
    public boolean isZero()                   { return amount.compareTo(BigDecimal.ZERO) == 0; }

    private void assertSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    public BigDecimal getAmount()  { return amount; }
    public String     getCurrency(){ return currency; }

    @Override public String toString()              { return String.format("%s %.2f", currency, amount); }
    @Override public boolean equals(Object o)       {
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }
    @Override public int hashCode()                 { return Objects.hash(amount.stripTrailingZeros(), currency); }
}
