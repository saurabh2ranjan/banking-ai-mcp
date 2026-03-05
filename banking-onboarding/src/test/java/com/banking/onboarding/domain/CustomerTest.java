package com.banking.onboarding.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer — domain entity")
class CustomerTest {

    private Customer buildCustomer(Customer.KycStatus kyc, Customer.OnboardingStatus ob) {
        return Customer.builder()
                .customerId("CUST-001")
                .firstName("Alice")
                .lastName("Johnson")
                .email("alice@example.com")
                .mobile("+447700900001")
                .kycStatus(kyc)
                .onboardingStatus(ob)
                .riskCategory(Customer.RiskCategory.LOW)
                .build();
    }

    // ─── isKycVerified ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isKycVerified()")
    class IsKycVerified {

        @Test
        void verifiedStatus_returnsTrue() {
            assertThat(buildCustomer(Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.KYC_VERIFIED)
                    .isKycVerified()).isTrue();
        }

        @ParameterizedTest(name = "status={0} → not verified")
        @EnumSource(value = Customer.KycStatus.class, names = {"PENDING", "UNDER_REVIEW", "REJECTED", "EXPIRED"})
        void nonVerifiedStatuses_returnFalse(Customer.KycStatus status) {
            assertThat(buildCustomer(status, Customer.OnboardingStatus.INITIATED).isKycVerified()).isFalse();
        }
    }

    // ─── isOnboardingComplete ─────────────────────────────────────────────────

    @Nested
    @DisplayName("isOnboardingComplete()")
    class IsOnboardingComplete {

        @Test
        void completedStatus_returnsTrue() {
            assertThat(buildCustomer(Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.COMPLETED)
                    .isOnboardingComplete()).isTrue();
        }

        @Test
        void accountCreatedStatus_alsoReturnsTrue() {
            assertThat(buildCustomer(Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.ACCOUNT_CREATED)
                    .isOnboardingComplete()).isTrue();
        }

        @ParameterizedTest(name = "status={0} → not complete")
        @EnumSource(value = Customer.OnboardingStatus.class,
                    names = {"INITIATED", "DOCUMENTS_SUBMITTED", "KYC_PENDING",
                             "KYC_VERIFIED", "REJECTED", "SUSPENDED"})
        void nonCompleteStatuses_returnFalse(Customer.OnboardingStatus status) {
            assertThat(buildCustomer(Customer.KycStatus.PENDING, status).isOnboardingComplete()).isFalse();
        }
    }

    // ─── getFullName ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getFullName() concatenates first and last name")
    void getFullName_concatenatesNames() {
        Customer c = buildCustomer(Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.COMPLETED);
        assertThat(c.getFullName()).isEqualTo("Alice Johnson");
    }

    // ─── Default risk category ────────────────────────────────────────────────

    @Test
    @DisplayName("Default risk category is LOW when not set explicitly")
    void defaultRiskCategory_isLow() {
        Customer c = Customer.builder()
                .customerId("CUST-002")
                .firstName("Bob").lastName("Smith")
                .kycStatus(Customer.KycStatus.PENDING)
                .onboardingStatus(Customer.OnboardingStatus.INITIATED)
                .build();
        assertThat(c.getRiskCategory()).isEqualTo(Customer.RiskCategory.LOW);
    }

    // ─── Enum completeness ────────────────────────────────────────────────────

    @Test
    @DisplayName("All required Gender values are present")
    void genderEnumValues() {
        assertThat(Customer.Gender.values())
                .containsExactlyInAnyOrder(
                        Customer.Gender.MALE,
                        Customer.Gender.FEMALE,
                        Customer.Gender.OTHER,
                        Customer.Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    @DisplayName("All required EmploymentType values are present")
    void employmentTypeEnumValues() {
        assertThat(Customer.EmploymentType.values())
                .containsExactlyInAnyOrder(
                        Customer.EmploymentType.SALARIED,
                        Customer.EmploymentType.SELF_EMPLOYED,
                        Customer.EmploymentType.BUSINESS_OWNER,
                        Customer.EmploymentType.RETIRED,
                        Customer.EmploymentType.STUDENT,
                        Customer.EmploymentType.UNEMPLOYED);
    }

    // ─── Mutable KYC fields ───────────────────────────────────────────────────

    @Test
    @DisplayName("KYC fields can be updated after creation")
    void kycFieldsMutable() {
        Customer c = buildCustomer(Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        c.setKycStatus(Customer.KycStatus.VERIFIED);
        c.setKycVerifiedAt(java.time.LocalDateTime.now());
        c.setOnboardingStatus(Customer.OnboardingStatus.KYC_VERIFIED);

        assertThat(c.isKycVerified()).isTrue();
        assertThat(c.getKycVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("KYC rejection stores reason")
    void kycRejectionReason_stored() {
        Customer c = buildCustomer(Customer.KycStatus.UNDER_REVIEW, Customer.OnboardingStatus.DOCUMENTS_SUBMITTED);
        c.setKycStatus(Customer.KycStatus.REJECTED);
        c.setKycRejectionReason("Photo ID was expired");
        c.setOnboardingStatus(Customer.OnboardingStatus.REJECTED);

        assertThat(c.getKycStatus()).isEqualTo(Customer.KycStatus.REJECTED);
        assertThat(c.getKycRejectionReason()).isEqualTo("Photo ID was expired");
        assertThat(c.isKycVerified()).isFalse();
        assertThat(c.isOnboardingComplete()).isFalse();
    }

    // ─── ID expiry ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("idExpiryDate can be set and retrieved")
    void idExpiryDate_settable() {
        Customer c = buildCustomer(Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.COMPLETED);
        LocalDate expiry = LocalDate.of(2035, 6, 30);
        c.setIdExpiryDate(expiry);
        assertThat(c.getIdExpiryDate()).isEqualTo(expiry);
    }
}
