package com.banking.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility for generating unique, formatted bank account numbers.
 * Format: YYYYMM-XXXXXX-C  where C is a Luhn check digit.
 */
public final class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger ACCOUNT_SEQ  = new AtomicInteger(RANDOM.nextInt(500_000));
    private static final AtomicInteger CUSTOMER_SEQ = new AtomicInteger(RANDOM.nextInt(50_000_000));

    private AccountNumberGenerator() {}

    public static String generate() {
        String prefix  = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String body    = String.format("%06d", ACCOUNT_SEQ.incrementAndGet() % 1_000_000);
        String partial = prefix + body;
        int    check   = luhnCheckDigit(partial);
        return "ACC-" + prefix + "-" + body + "-" + check;
    }

    public static String generateCustomerId() {
        return "CUST-" + String.format("%08d", CUSTOMER_SEQ.incrementAndGet() % 100_000_000);
    }

    private static int luhnCheckDigit(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(number.charAt(i))) continue;
            int n = number.charAt(i) - '0';
            if (alternate) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }
}
