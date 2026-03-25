package com.banking.onboarding.service;

import com.banking.common.exception.BankingExceptions.*;
import com.banking.common.util.AccountNumberGenerator;
import com.banking.events.EventMetadata;
import com.banking.events.notification.EmailNotificationEvent;
import com.banking.events.onboarding.KycStatusChangedEvent;
import com.banking.notification.service.NotificationService;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.domain.Customer.KycStatus;
import com.banking.onboarding.domain.Customer.OnboardingStatus;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.mapper.CustomerMapper;
import com.banking.onboarding.repository.CustomerRepository;
import com.banking.onboarding.validator.KycValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOnboardingService {

    private final CustomerRepository      customerRepository;
    private final CustomerMapper          customerMapper;
    private final KycValidator            kycValidator;
    private final NotificationService     notificationService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Onboarding Workflow ──────────────────────────────────────────────

    @Transactional
    public OnboardingResponse initiateOnboarding(OnboardingRequest request) {
        log.info("Initiating onboarding for email: {}", request.email());

        // Duplicate checks
        if (customerRepository.existsByEmail(request.email()))
            throw new DuplicateResourceException("Customer", "email: " + request.email());
        if (customerRepository.existsByMobile(request.mobile()))
            throw new DuplicateResourceException("Customer", "mobile: " + request.mobile());
        if (request.panNumber() != null && customerRepository.existsByPanNumber(request.panNumber()))
            throw new DuplicateResourceException("Customer", "PAN: " + request.panNumber());

        // KYC pre-validation
        kycValidator.validate(request);

        // Build and persist customer
        Customer customer = customerMapper.toEntity(request);
        customer.setCustomerId(AccountNumberGenerator.generateCustomerId());

        Customer saved = customerRepository.save(customer);
        log.info("Customer created: {}", saved.getCustomerId());

        // Async notification (fire-and-forget in real system)
        notificationService.sendWelcomeEmail(saved.getEmail(), saved.getFullName());

        eventPublisher.publishEvent(new EmailNotificationEvent(
                saved.getCustomerId(), saved.getEmail(), saved.getFullName(),
                "Welcome", "Welcome to our bank, " + saved.getFullName() + "!",
                EventMetadata.now("banking-onboarding", MDC.get("traceId"))));

        return new OnboardingResponse(
            saved.getCustomerId(),
            saved.getOnboardingStatus().name(),
            "Onboarding initiated successfully. Please submit KYC documents.",
            "SUBMIT_DOCUMENTS"
        );
    }

    @Transactional
    public CustomerResponse updateKycStatus(KycUpdateRequest request) {
        Customer customer = findCustomerById(request.customerId());

        KycStatus newStatus = request.kycStatus();
        customer.setKycStatus(newStatus);

        String previousKycStatus = customer.getKycStatus() != null ? customer.getKycStatus().name() : null;

        if (KycStatus.VERIFIED.equals(newStatus)) {
            customer.setKycVerifiedAt(LocalDateTime.now());
            customer.setOnboardingStatus(OnboardingStatus.KYC_VERIFIED);
            kycValidator.screenForAml(customer);
            notificationService.sendKycApprovedEmail(customer.getEmail(), customer.getFullName());
            log.info("KYC verified for customer: {}", customer.getCustomerId());

        } else if (KycStatus.REJECTED.equals(newStatus)) {
            customer.setKycRejectionReason(request.rejectionReason());
            customer.setOnboardingStatus(OnboardingStatus.REJECTED);
            notificationService.sendKycRejectedEmail(
                customer.getEmail(), customer.getFullName(), request.rejectionReason());
            log.warn("KYC rejected for customer: {}, reason: {}", customer.getCustomerId(), request.rejectionReason());
        }

        CustomerResponse response = customerMapper.toResponse(customerRepository.save(customer));

        eventPublisher.publishEvent(new KycStatusChangedEvent(
                customer.getCustomerId(), customer.getEmail(), customer.getFullName(),
                previousKycStatus, newStatus.name(), request.rejectionReason(),
                EventMetadata.now("banking-onboarding", MDC.get("traceId"))));
        eventPublisher.publishEvent(new EmailNotificationEvent(
                customer.getCustomerId(), customer.getEmail(), customer.getFullName(),
                "KYC Status Update", "Your KYC status has been updated to: " + newStatus.name(),
                EventMetadata.now("banking-onboarding", MDC.get("traceId"))));

        return response;
    }

    @Transactional
    public CustomerResponse completeOnboarding(String customerId) {
        Customer customer = findCustomerById(customerId);

        if (!customer.isKycVerified()) {
            throw new KycNotApprovedException(customerId);
        }

        customer.setOnboardingStatus(OnboardingStatus.COMPLETED);
        customer.setOnboardingCompletedAt(LocalDateTime.now());
        notificationService.sendOnboardingCompleteEmail(customer.getEmail(), customer.getFullName());

        eventPublisher.publishEvent(new EmailNotificationEvent(
                customer.getCustomerId(), customer.getEmail(), customer.getFullName(),
                "Onboarding Complete", "Congratulations! Your onboarding is now complete.",
                EventMetadata.now("banking-onboarding", MDC.get("traceId"))));

        return customerMapper.toResponse(customerRepository.save(customer));
    }

    // ─── Queries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(String customerId) {
        return customerMapper.toResponse(findCustomerById(customerId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByEmail(String email) {
        return customerMapper.toResponse(
            customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException(email))
        );
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummary> getCustomersByStatus(OnboardingStatus status, Pageable pageable) {
        return customerRepository.findByOnboardingStatus(status, pageable)
                                 .map(customerMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummary> getPendingKycCustomers(Pageable pageable) {
        return customerRepository.findPendingKycCustomers(pageable)
                                 .map(customerMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntity(String customerId) {
        return findCustomerById(customerId);
    }

    // ─── Internal ─────────────────────────────────────────────────────────

    private Customer findCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
