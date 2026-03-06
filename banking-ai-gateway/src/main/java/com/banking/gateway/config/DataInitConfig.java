package com.banking.gateway.config;

import com.banking.account.domain.Account;
import com.banking.account.repository.AccountRepository;
import com.banking.onboarding.domain.Address;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds demo data on startup for local H2 development.
 * In production, this is replaced by Flyway migrations (V1 + V2).
 *
 * Activate with: spring.profiles.active=dev  (or default with H2)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("dev")
public class DataInitConfig {

    @Bean
    CommandLineRunner seedDemoData(CustomerRepository customerRepository,
                                   AccountRepository  accountRepository) {
        return args -> {
            if (customerRepository.count() > 0) {
                log.info("Demo data already present — skipping seed");
                return;
            }

            log.info("Seeding demo data...");

            // ── Demo Customers ────────────────────────────────────────────

            Customer alice = customerRepository.save(Customer.builder()
                .customerId("CUST-00000001")
                .firstName("Alice").lastName("Johnson")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender(Customer.Gender.FEMALE)
                .email("alice.johnson@demo.com")
                .mobile("+447700900001")
                .nationality("British")
                .panNumber("ABCDE1234F")
                .idType(Customer.IdDocumentType.PAN_CARD)
                .idExpiryDate(LocalDate.of(2030, 12, 31))
                .kycStatus(Customer.KycStatus.VERIFIED)
                .kycVerifiedAt(LocalDateTime.now().minusDays(10))
                .riskCategory(Customer.RiskCategory.LOW)
                .onboardingStatus(Customer.OnboardingStatus.COMPLETED)
                .onboardingCompletedAt(LocalDateTime.now().minusDays(9))
                .address(Address.builder()
                    .line1("123 High Street").city("London")
                    .state("England").postalCode("EC1A 1BB").country("GBR").build())
                .employmentType(Customer.EmploymentType.SALARIED)
                .employerName("Tech Corp Ltd")
                .annualIncome(new BigDecimal("95000"))
                .incomeCurrency("GBP")
                .build());

            Customer bob = customerRepository.save(Customer.builder()
                .customerId("CUST-00000002")
                .firstName("Bob").lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 3, 20))
                .gender(Customer.Gender.MALE)
                .email("bob.smith@demo.com")
                .mobile("+447700900002")
                .nationality("British")
                .panNumber("XYZPQ5678G")
                .idType(Customer.IdDocumentType.PAN_CARD)
                .idExpiryDate(LocalDate.of(2029, 6, 30))
                .kycStatus(Customer.KycStatus.VERIFIED)
                .kycVerifiedAt(LocalDateTime.now().minusDays(5))
                .riskCategory(Customer.RiskCategory.LOW)
                .onboardingStatus(Customer.OnboardingStatus.COMPLETED)
                .onboardingCompletedAt(LocalDateTime.now().minusDays(4))
                .address(Address.builder()
                    .line1("45 Park Lane").city("Manchester")
                    .state("England").postalCode("M1 1AA").country("GBR").build())
                .employmentType(Customer.EmploymentType.SELF_EMPLOYED)
                .employerName("Smith Consulting")
                .annualIncome(new BigDecimal("120000"))
                .incomeCurrency("GBP")
                .build());

            Customer charlie = customerRepository.save(Customer.builder()
                .customerId("CUST-00000003")
                .firstName("Charlie").lastName("Corporate")
                .dateOfBirth(LocalDate.of(1978, 11, 8))
                .gender(Customer.Gender.MALE)
                .email("charlie.corp@acme.com")
                .mobile("+12125550001")
                .nationality("American")
                .passportNumber("US123456789")
                .idType(Customer.IdDocumentType.PASSPORT)
                .idExpiryDate(LocalDate.of(2031, 1, 15))
                .kycStatus(Customer.KycStatus.VERIFIED)
                .kycVerifiedAt(LocalDateTime.now().minusDays(30))
                .riskCategory(Customer.RiskCategory.MEDIUM)
                .onboardingStatus(Customer.OnboardingStatus.COMPLETED)
                .onboardingCompletedAt(LocalDateTime.now().minusDays(29))
                .address(Address.builder()
                    .line1("1 Wall Street").city("New York")
                    .state("New York").postalCode("10005").country("USA").build())
                .employmentType(Customer.EmploymentType.BUSINESS_OWNER)
                .employerName("Acme Industries")
                .annualIncome(new BigDecimal("500000"))
                .incomeCurrency("USD")
                .build());

            // KYC pending demo customer
            customerRepository.save(Customer.builder()
                .customerId("CUST-00000004")
                .firstName("Diana").lastName("Pending")
                .dateOfBirth(LocalDate.of(1995, 7, 22))
                .gender(Customer.Gender.FEMALE)
                .email("diana.pending@example.com")
                .mobile("+447700900004")
                .nationality("British")
                .panNumber("RSTUV3456I")
                .idType(Customer.IdDocumentType.PAN_CARD)
                .idExpiryDate(LocalDate.of(2032, 3, 31))
                .kycStatus(Customer.KycStatus.UNDER_REVIEW)
                .riskCategory(Customer.RiskCategory.LOW)
                .onboardingStatus(Customer.OnboardingStatus.DOCUMENTS_SUBMITTED)
                .address(Address.builder()
                    .line1("78 Oak Avenue").city("Birmingham")
                    .state("England").postalCode("B1 1BB").country("GBR").build())
                .employmentType(Customer.EmploymentType.SALARIED)
                .employerName("Finance House")
                .annualIncome(new BigDecimal("55000"))
                .incomeCurrency("GBP")
                .build());

            // ── Demo Accounts ─────────────────────────────────────────────

            accountRepository.save(Account.builder()
                .accountId("ACC-202401-001001-5")
                .accountNumber("GB29BANK12345600000001")
                .customerId(alice.getCustomerId())
                .displayName("Alice Savings")
                .accountType(Account.AccountType.SAVINGS)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("85000.00"))
                .availableBalance(new BigDecimal("85000.00"))
                .holdAmount(BigDecimal.ZERO)
                .currency("GBP")
                .dailyDebitLimit(new BigDecimal("10000"))
                .singleTransactionLimit(new BigDecimal("5000"))
                .minimumBalance(new BigDecimal("500"))
                .interestRate(new BigDecimal("0.0350"))
                .openedDate(LocalDate.now().minusDays(9))
                .build());

            accountRepository.save(Account.builder()
                .accountId("ACC-202401-002001-3")
                .accountNumber("GB29BANK12345600000002")
                .customerId(alice.getCustomerId())
                .displayName("Alice Current")
                .accountType(Account.AccountType.CURRENT)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("12500.00"))
                .availableBalance(new BigDecimal("12500.00"))
                .holdAmount(BigDecimal.ZERO)
                .currency("GBP")
                .dailyDebitLimit(new BigDecimal("50000"))
                .singleTransactionLimit(new BigDecimal("25000"))
                .minimumBalance(new BigDecimal("1000"))
                .interestRate(BigDecimal.ZERO)
                .openedDate(LocalDate.now().minusDays(8))
                .build());

            accountRepository.save(Account.builder()
                .accountId("ACC-202401-003001-7")
                .accountNumber("GB29BANK12345600000003")
                .customerId(bob.getCustomerId())
                .displayName("Bob Current")
                .accountType(Account.AccountType.CURRENT)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("250000.00"))
                .availableBalance(new BigDecimal("250000.00"))
                .holdAmount(BigDecimal.ZERO)
                .currency("GBP")
                .dailyDebitLimit(new BigDecimal("100000"))
                .singleTransactionLimit(new BigDecimal("50000"))
                .minimumBalance(new BigDecimal("5000"))
                .interestRate(BigDecimal.ZERO)
                .openedDate(LocalDate.now().minusDays(4))
                .build());

            accountRepository.save(Account.builder()
                .accountId("ACC-202401-004001-2")
                .accountNumber("US29BANK12345600000004")
                .customerId(charlie.getCustomerId())
                .displayName("Acme Business Account")
                .accountType(Account.AccountType.CURRENT)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("1500000.00"))
                .availableBalance(new BigDecimal("1500000.00"))
                .holdAmount(BigDecimal.ZERO)
                .currency("USD")
                .dailyDebitLimit(new BigDecimal("500000"))
                .singleTransactionLimit(new BigDecimal("200000"))
                .minimumBalance(new BigDecimal("10000"))
                .interestRate(BigDecimal.ZERO)
                .openedDate(LocalDate.now().minusDays(29))
                .build());

            log.info("✅ Demo data seeded:");
            log.info("   Customers: CUST-00000001 (Alice), CUST-00000002 (Bob), CUST-00000003 (Charlie), CUST-00000004 (Diana - KYC pending)");
            log.info("   Accounts : ACC-202401-001001-5 (Alice Savings £85k), ACC-202401-002001-3 (Alice Current £12.5k)");
            log.info("              ACC-202401-003001-7 (Bob Current £250k), ACC-202401-004001-2 (Charlie USD $1.5M)");
        };
    }
}
