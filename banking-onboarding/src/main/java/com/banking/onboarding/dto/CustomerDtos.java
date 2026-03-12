package com.banking.onboarding.dto;

import com.banking.onboarding.domain.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTOs for the customer onboarding workflow.
 * Separates API contract from domain model.
 */
public class CustomerDtos {

    // ─── Request DTOs ─────────────────────────────────────────────────────

    public record OnboardingRequest(

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50)
        String lastName,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull
        Customer.Gender gender,

        @NotBlank
        @Email(message = "Valid email is required")
        String email,

        @NotBlank
        @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Valid mobile number required")
        String mobile,

        @NotBlank
        @Size(min = 2, max = 50)
        String nationality,

        String panNumber,
        String passportNumber,
        String nationalId,

        @NotNull
        Customer.IdDocumentType idType,

        @Future(message = "ID must not be expired")
        LocalDate idExpiryDate,

        @NotNull @Valid
        AddressRequest address,

        Customer.EmploymentType employmentType,
        String employerName,

        @DecimalMin(value = "0.0")
        BigDecimal annualIncome,

        @Size(min = 3, max = 3)
        String incomeCurrency,

        String preferredAccountType   // SAVINGS / CURRENT
    ) {}

    public record AddressRequest(
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String postalCode,
        @NotBlank @Size(min = 3, max = 3) String country
    ) {}

    public record KycUpdateRequest(
        String customerId,           // populated from path variable by controller, not from request body
        @NotNull Customer.KycStatus kycStatus,
        String rejectionReason
    ) {}

    // ─── Response DTOs ────────────────────────────────────────────────────

    public record CustomerResponse(
        String customerId,
        String firstName,
        String lastName,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String email,
        String mobile,
        String nationality,
        String panNumber,
        String idType,
        LocalDate idExpiryDate,
        String kycStatus,
        String onboardingStatus,
        String riskCategory,
        AddressResponse address,
        String employmentType,
        String employerName,
        BigDecimal annualIncome,
        String incomeCurrency,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
    ) {}

    public record AddressResponse(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        String formatted
    ) {}

    public record OnboardingResponse(
        String customerId,
        String status,
        String message,
        String nextStep
    ) {}

    public record CustomerSummary(
        String customerId,
        String fullName,
        String email,
        String mobile,
        String kycStatus,
        String onboardingStatus,
        java.time.LocalDateTime createdAt
    ) {}
}
