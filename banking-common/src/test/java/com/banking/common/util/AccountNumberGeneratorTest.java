package com.banking.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountNumberGenerator")
class AccountNumberGeneratorTest {

    // ─── generate() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() — starts with ACC-")
    void generate_startsWithAccPrefix() {
        assertThat(AccountNumberGenerator.generate()).startsWith("ACC-");
    }

    @Test
    @DisplayName("generate() — matches format ACC-YYYYMM-NNNNNN-C")
    void generate_matchesFormat() {
        assertThat(AccountNumberGenerator.generate()).matches("ACC-\\d{6}-\\d{6}-\\d");
    }

    @Test
    @DisplayName("generate() — embeds current year-month")
    void generate_containsCurrentYearMonth() {
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        assertThat(AccountNumberGenerator.generate()).contains(ym);
    }

    @Test
    @DisplayName("generate() — check digit is 0-9")
    void generate_checkDigitInRange() {
        String id = AccountNumberGenerator.generate();
        int checkDigit = Character.getNumericValue(id.charAt(id.length() - 1));
        assertThat(checkDigit).isBetween(0, 9);
    }

    @RepeatedTest(5)
    @DisplayName("generate() — produces 200 unique IDs in a batch")
    void generate_producesUniqueIds() {
        Set<String> ids = IntStream.range(0, 200)
                .mapToObj(i -> AccountNumberGenerator.generate())
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(200);
    }

    // ─── generateCustomerId() ─────────────────────────────────────────────────

    @Test
    @DisplayName("generateCustomerId() — starts with CUST-")
    void generateCustomerId_startsWithCustPrefix() {
        assertThat(AccountNumberGenerator.generateCustomerId()).startsWith("CUST-");
    }

    @Test
    @DisplayName("generateCustomerId() — matches format CUST-NNNNNNNN")
    void generateCustomerId_matchesFormat() {
        assertThat(AccountNumberGenerator.generateCustomerId()).matches("CUST-\\d{8}");
    }

    @RepeatedTest(3)
    @DisplayName("generateCustomerId() — produces 500 unique IDs in a batch")
    void generateCustomerId_producesUniqueIds() {
        Set<String> ids = IntStream.range(0, 500)
                .mapToObj(i -> AccountNumberGenerator.generateCustomerId())
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(500);
    }
}
