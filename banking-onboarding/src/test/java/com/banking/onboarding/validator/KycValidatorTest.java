package com.banking.onboarding.validator;

import com.banking.common.exception.BankingExceptions.KycFailedException;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.AddressRequest;
import com.banking.onboarding.dto.CustomerDtos.OnboardingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KycValidator")
class KycValidatorTest {

    private KycValidator validator;
    private AddressRequest validAddress;

    @BeforeEach
    void setUp() {
        validator     = new KycValidator();
        validAddress  = new AddressRequest("123 Main St", null, "London", "England", "EC1A 1BB", "GBR");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OnboardingRequest validPanRequest(String email, String mobile) {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, email, mobile, "British",
            "ABCDE1234F", null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 12, 31),
            validAddress,
            Customer.EmploymentType.SALARIED, "Tech Corp",
            new BigDecimal("80000"), "GBP", "SAVINGS"
        );
    }

    private OnboardingRequest withDob(LocalDate dob) {
        return new OnboardingRequest(
            "Alice", "Johnson", dob,
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
            "ABCDE1234F", null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 12, 31),
            validAddress, null, null, null, null, null
        );
    }

    private OnboardingRequest withPan(String pan) {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
            pan, null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 12, 31),
            validAddress, null, null, null, null, null
        );
    }

    private OnboardingRequest withIdExpiry(LocalDate expiry) {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
            "ABCDE1234F", null, null,
            Customer.IdDocumentType.PAN_CARD, expiry,
            validAddress, null, null, null, null, null
        );
    }

    // ─── Valid requests ───────────────────────────────────────────────────────

    @Nested @DisplayName("Valid requests")
    class ValidRequests {

        @Test void panCard_passes() {
            assertThatCode(() -> validator.validate(validPanRequest("a@b.com", "+447700900001")))
                    .doesNotThrowAnyException();
        }

        @Test void passportOnly_passes() {
            var req = new OnboardingRequest(
                "Bob", "Smith", LocalDate.of(1985, 3, 20),
                Customer.Gender.MALE, "bob@example.com", "+447700900002", "British",
                null, "GB12345678", null,
                Customer.IdDocumentType.PASSPORT, LocalDate.of(2029, 6, 30),
                validAddress, null, null, null, null, null
            );
            assertThatCode(() -> validator.validate(req)).doesNotThrowAnyException();
        }

        @Test void nationalIdOnly_passes() {
            var req = new OnboardingRequest(
                "Carol", "Davis", LocalDate.of(1992, 7, 10),
                Customer.Gender.FEMALE, "carol@example.com", "+447700900003", "British",
                null, null, "NID123456789",
                Customer.IdDocumentType.NATIONAL_ID, LocalDate.of(2031, 1, 1),
                validAddress, null, null, null, null, null
            );
            assertThatCode(() -> validator.validate(req)).doesNotThrowAnyException();
        }
    }

    // ─── Age validation ───────────────────────────────────────────────────────

    @Nested @DisplayName("Age validation")
    class AgeValidation {

        @Test void under18_fails() {
            assertThatThrownBy(() -> validator.validate(withDob(LocalDate.now().minusYears(17))))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("18 years old");
        }

        @Test void exactly18_passes() {
            assertThatCode(() -> validator.validate(withDob(LocalDate.now().minusYears(18))))
                    .doesNotThrowAnyException();
        }

        @Test void age121_failsAsInvalidDob() {
            assertThatThrownBy(() -> validator.validate(withDob(LocalDate.now().minusYears(121))))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("date of birth");
        }

        @Test void normalAdultAge_passes() {
            assertThatCode(() -> validator.validate(withDob(LocalDate.of(1985, 6, 15))))
                    .doesNotThrowAnyException();
        }
    }

    // ─── PAN card format ──────────────────────────────────────────────────────

    @Nested @DisplayName("PAN card format")
    class PanFormat {

        @ParameterizedTest
        @ValueSource(strings = {"ABCDE1234F", "XYZPQ9876G", "LMNOP5432H", "RSTUV3456I"})
        void validPanFormats_pass(String pan) {
            assertThatCode(() -> validator.validate(withPan(pan))).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ABC123456", "abcde1234f", "ABCDE12345", "12345ABCDE", "ABCDE123", "ABCDE1234FF"})
        void invalidPanFormats_fail(String pan) {
            assertThatThrownBy(() -> validator.validate(withPan(pan)))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("PAN card format");
        }
    }

    // ─── Identity document ────────────────────────────────────────────────────

    @Nested @DisplayName("Identity document")
    class IdentityDocument {

        @Test void noId_fails() {
            var req = new OnboardingRequest(
                "Nobody", "Here", LocalDate.of(1990, 1, 1),
                Customer.Gender.MALE, "nobody@x.com", "+447700900099", "British",
                null, null, null,
                Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 1, 1),
                validAddress, null, null, null, null, null
            );
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("government-issued ID");
        }

        @Test void expiredId_fails() {
            assertThatThrownBy(() -> validator.validate(withIdExpiry(LocalDate.of(2020, 1, 1))))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("expired");
        }

        @Test void futureExpiry_passes() {
            assertThatCode(() -> validator.validate(withIdExpiry(LocalDate.now().plusYears(5))))
                    .doesNotThrowAnyException();
        }
    }

    // ─── Existing customer validation ─────────────────────────────────────────

    @Nested @DisplayName("validateExistingCustomer")
    class ExistingCustomer {

        @Test void lowRiskValidDoc_passes() {
            var c = Customer.builder()
                    .customerId("CUST-001")
                    .riskCategory(Customer.RiskCategory.LOW)
                    .idExpiryDate(LocalDate.now().plusYears(5))
                    .build();
            assertThatCode(() -> validator.validateExistingCustomer(c)).doesNotThrowAnyException();
        }

        @Test void prohibited_fails() {
            var c = Customer.builder()
                    .customerId("CUST-BAD")
                    .riskCategory(Customer.RiskCategory.PROHIBITED)
                    .idExpiryDate(LocalDate.now().plusYears(5))
                    .build();
            assertThatThrownBy(() -> validator.validateExistingCustomer(c))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("prohibited list");
        }

        @Test void expiredDocument_fails() {
            var c = Customer.builder()
                    .customerId("CUST-EXP")
                    .riskCategory(Customer.RiskCategory.LOW)
                    .idExpiryDate(LocalDate.now().minusDays(1))
                    .build();
            assertThatThrownBy(() -> validator.validateExistingCustomer(c))
                    .isInstanceOf(KycFailedException.class)
                    .hasMessageContaining("expired");
        }

        @Test void noExpiryDate_passes() {
            var c = Customer.builder()
                    .customerId("CUST-001")
                    .riskCategory(Customer.RiskCategory.LOW)
                    .build(); // idExpiryDate null
            assertThatCode(() -> validator.validateExistingCustomer(c)).doesNotThrowAnyException();
        }
    }

    // ─── AML screening ────────────────────────────────────────────────────────

    @Test @DisplayName("screenForAml always returns true in demo mode")
    void amlScreening_alwaysTrue() {
        assertThat(validator.screenForAml(Customer.builder().customerId("C1").build())).isTrue();
    }

    // ─── Multiple violations reported together ────────────────────────────────

    @Test @DisplayName("Multiple violations combined in single exception message")
    void multipleViolations_reportedTogether() {
        var req = new OnboardingRequest(
            "Young", "NoPan", LocalDate.now().minusYears(16),   // under 18
            Customer.Gender.MALE, "y@x.com", "+447700900001", "British",
            null, null, null,                                    // no ID
            null, null, validAddress, null, null, null, null, null
        );
        assertThatThrownBy(() -> validator.validate(req))
                .isInstanceOf(KycFailedException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    assertThat(msg).contains("18 years old");
                    assertThat(msg).contains("government-issued ID");
                });
    }
}
