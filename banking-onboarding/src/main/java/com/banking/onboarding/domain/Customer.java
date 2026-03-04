package com.banking.onboarding.domain;

import com.banking.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Core Customer entity holding all personal, identity, and KYC data.
 * One customer may hold multiple accounts after successful onboarding.
 */
@Entity
@Table(
    name = "customers",
    indexes = {
        @Index(name = "idx_customer_email",     columnList = "email",       unique = true),
        @Index(name = "idx_customer_mobile",    columnList = "mobile",      unique = true),
        @Index(name = "idx_customer_pan",       columnList = "pan_number",  unique = true),
        @Index(name = "idx_customer_status",    columnList = "onboarding_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @Id
    @Column(name = "customer_id", length = 20)
    private String customerId;

    // ─── Personal Info ────────────────────────────────────────────────────
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, unique = true, length = 15)
    private String mobile;

    @Column(name = "nationality", length = 50)
    private String nationality;

    // ─── KYC / Identity ──────────────────────────────────────────────────
    @Column(name = "pan_number", unique = true, length = 10)
    private String panNumber;              // Permanent Account Number (India)

    @Column(name = "passport_number", length = 20)
    private String passportNumber;

    @Column(name = "national_id", length = 30)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", length = 20)
    private IdDocumentType idType;

    @Column(name = "id_expiry_date")
    private LocalDate idExpiryDate;

    // ─── KYC Status ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_verified_at")
    private java.time.LocalDateTime kycVerifiedAt;

    @Column(name = "kyc_rejection_reason", length = 500)
    private String kycRejectionReason;

    // ─── Risk Classification ──────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", length = 20)
    @Builder.Default
    private RiskCategory riskCategory = RiskCategory.LOW;

    // ─── Onboarding ───────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 30)
    private OnboardingStatus onboardingStatus;

    @Column(name = "onboarding_completed_at")
    private java.time.LocalDateTime onboardingCompletedAt;

    // ─── Address ─────────────────────────────────────────────────────────
    @Embedded
    private Address address;

    // ─── Employment ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private EmploymentType employmentType;

    @Column(name = "employer_name", length = 100)
    private String employerName;

    @Column(name = "annual_income")
    private java.math.BigDecimal annualIncome;

    @Column(name = "income_currency", length = 3)
    private String incomeCurrency;

    // ─── Relationships ────────────────────────────────────────────────────
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CustomerDocument> documents = new ArrayList<>();

    // ─── Enums ────────────────────────────────────────────────────────────
    public enum Gender           { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
    public enum IdDocumentType   { PASSPORT, NATIONAL_ID, DRIVING_LICENSE, PAN_CARD, AADHAAR }
    public enum KycStatus        { PENDING, UNDER_REVIEW, VERIFIED, REJECTED, EXPIRED }
    public enum RiskCategory     { LOW, MEDIUM, HIGH, PROHIBITED }
    public enum OnboardingStatus { INITIATED, DOCUMENTS_SUBMITTED, KYC_PENDING, KYC_VERIFIED, ACCOUNT_CREATED, COMPLETED, REJECTED, SUSPENDED }
    public enum EmploymentType   { SALARIED, SELF_EMPLOYED, BUSINESS_OWNER, RETIRED, STUDENT, UNEMPLOYED }

    // ─── Helpers ──────────────────────────────────────────────────────────
    public String getFullName() { return firstName + " " + lastName; }

    public boolean isKycVerified() { return KycStatus.VERIFIED.equals(kycStatus); }

    public boolean isOnboardingComplete() {
        return OnboardingStatus.COMPLETED.equals(onboardingStatus) || OnboardingStatus.ACCOUNT_CREATED.equals(onboardingStatus);
    }
}
