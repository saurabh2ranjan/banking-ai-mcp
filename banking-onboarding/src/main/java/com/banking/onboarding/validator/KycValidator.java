package com.banking.onboarding.validator;

import com.banking.common.exception.BankingExceptions.KycFailedException;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.OnboardingRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Business-rule KYC validation.
 * In production this would call an external identity bureau API
 * (e.g. Experian, Equifax, Onfido, or a government ID-verification service).
 */
@Slf4j
@Component
public class KycValidator {

    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 120;

    public void validate(OnboardingRequest request) {
        List<String> violations = new ArrayList<>();

        validateAge(request.dateOfBirth(), violations);
        validateIdentityDocument(request, violations);
        validateContactInfo(request, violations);

        if (!violations.isEmpty()) {
            String reason = String.join("; ", violations);
            log.warn("KYC validation failed: {}", reason);
            throw new KycFailedException(reason);
        }
    }

    public void validateExistingCustomer(Customer customer) {
        List<String> violations = new ArrayList<>();

        if (customer.getIdExpiryDate() != null && customer.getIdExpiryDate().isBefore(LocalDate.now())) {
            violations.add("Identity document has expired");
        }
        if (Customer.RiskCategory.PROHIBITED.equals(customer.getRiskCategory())) {
            violations.add("Customer is on prohibited list");
        }

        if (!violations.isEmpty()) {
            throw new KycFailedException(String.join("; ", violations));
        }
    }

    private void validateAge(LocalDate dob, List<String> violations) {
        if (dob == null) { violations.add("Date of birth is required"); return; }
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < MIN_AGE) violations.add("Customer must be at least " + MIN_AGE + " years old (age: " + age + ")");
        if (age > MAX_AGE) violations.add("Invalid date of birth");
    }

    private void validateIdentityDocument(OnboardingRequest req, List<String> violations) {
        boolean hasId = StringUtils.isNotBlank(req.panNumber())
                     || StringUtils.isNotBlank(req.passportNumber())
                     || StringUtils.isNotBlank(req.nationalId());
        if (!hasId) {
            violations.add("At least one government-issued ID is required (PAN, Passport, or National ID)");
        }
        if (req.idExpiryDate() != null && req.idExpiryDate().isBefore(LocalDate.now())) {
            violations.add("Identity document is expired");
        }
        // PAN card format validation (India): ABCDE1234F
        if (StringUtils.isNotBlank(req.panNumber()) && !req.panNumber().matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
            violations.add("Invalid PAN card format");
        }
    }

    private void validateContactInfo(OnboardingRequest req, List<String> violations) {
        if (StringUtils.isBlank(req.email())) violations.add("Email is required");
        if (StringUtils.isBlank(req.mobile())) violations.add("Mobile number is required");
    }

    /**
     * Simulates external AML (Anti-Money Laundering) screening.
     * In production: call OFAC SDN list, PEP database, etc.
     */
    public boolean screenForAml(Customer customer) {
        // Placeholder — always passes in demo
        log.info("AML screening passed for customer: {}", customer.getCustomerId());
        return true;
    }
}
