package com.banking.onboarding.service;

import com.banking.common.exception.BankingExceptions.*;
import com.banking.notification.service.NotificationService;
import com.banking.onboarding.domain.Address;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.mapper.CustomerMapper;
import com.banking.onboarding.repository.CustomerRepository;
import com.banking.onboarding.validator.KycValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerOnboardingService")
class CustomerOnboardingServiceTest {

    @Mock CustomerRepository  customerRepository;
    @Mock CustomerMapper      customerMapper;
    @Mock KycValidator        kycValidator;
    @Mock NotificationService notificationService;

    @InjectMocks CustomerOnboardingService service;

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private Customer buildCustomer(String id, Customer.KycStatus kyc, Customer.OnboardingStatus ob) {
        return Customer.builder()
                .customerId(id)
                .firstName("Alice").lastName("Johnson")
                .email("alice@example.com").mobile("+447700900001")
                .kycStatus(kyc).onboardingStatus(ob)
                .riskCategory(Customer.RiskCategory.LOW)
                .address(Address.builder()
                        .line1("123 St").city("London").state("England")
                        .postalCode("EC1").country("GBR").build())
                .build();
    }

    private OnboardingRequest validRequest() {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
            "ABCDE1234F", null, null,
            Customer.IdDocumentType.PAN_CARD, LocalDate.of(2030, 12, 31),
            new AddressRequest("123 St", null, "London", "England", "EC1", "GBR"),
            Customer.EmploymentType.SALARIED, "Tech Corp",
            new BigDecimal("80000"), "GBP", "SAVINGS");
    }

    private CustomerResponse dummyResponse(Customer c) {
        return new CustomerResponse(
            c.getCustomerId(), "Alice", "Johnson", "Alice Johnson",
            LocalDate.of(1990, 5, 15), "FEMALE",
            "alice@example.com", "+447700900001",
            "British", "ABCDE1234F", "PAN_CARD", LocalDate.of(2030, 12, 31),
            c.getKycStatus().name(), c.getOnboardingStatus().name(), "LOW",
            null, "SALARIED", "Tech Corp", new BigDecimal("80000"), "GBP",
            LocalDateTime.now(), LocalDateTime.now());
    }

    private CustomerSummary dummySummary(Customer c) {
        return new CustomerSummary(
            c.getCustomerId(), "Alice Johnson",
            "alice@example.com", "+447700900001",
            c.getKycStatus().name(), c.getOnboardingStatus().name(),
            LocalDateTime.now());
    }

    // ─── initiateOnboarding ───────────────────────────────────────────────────

    @Nested @DisplayName("initiateOnboarding")
    class InitiateOnboarding {

    @Test
        void success_generatesId_savesAndNotifies() {
        Customer entity = buildCustomer(null, Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByMobile(anyString())).thenReturn(false);
        when(customerRepository.existsByPanNumber(anyString())).thenReturn(false);
        when(customerMapper.toEntity(any())).thenReturn(entity);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(saved.capture())).thenReturn(entity);

        OnboardingResponse resp = service.initiateOnboarding(validRequest());

        assertThat(resp.status()).isEqualTo("INITIATED");
        assertThat(resp.nextStep()).isEqualTo("SUBMIT_DOCUMENTS");
        assertThat(saved.getValue().getCustomerId()).matches("CUST-\\d{8}");
        verify(kycValidator).validate(any());
        verify(notificationService).sendWelcomeEmail(eq("alice@example.com"), anyString());
    }

    @Test
    void initiateOnboarding_kycValidationCalledBeforePersistence() {
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByMobile(anyString())).thenReturn(false);
        Customer entity = buildCustomer(null, Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        when(customerMapper.toEntity(any())).thenReturn(entity);
        when(customerRepository.save(any())).thenReturn(entity);

        service.initiateOnboarding(validRequest());

        var inOrder = inOrder(kycValidator, customerRepository);
        inOrder.verify(kycValidator).validate(any());
        inOrder.verify(customerRepository).save(any());
    }

    @Test
    void initiateOnboarding_duplicateEmail_throwsDuplicateResourceException() {
        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.initiateOnboarding(validRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email: alice@example.com");
        verify(customerRepository, never()).save(any());
    }

    @Test
        void duplicateMobile_throwsDuplicateResource() {
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByMobile("+447700900001")).thenReturn(true);
        assertThatThrownBy(() -> service.initiateOnboarding(validRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("mobile");
    }

    @Test
        void duplicatePan_throwsDuplicateResource() {
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByMobile(anyString())).thenReturn(false);
        when(customerRepository.existsByPanNumber("ABCDE1234F")).thenReturn(true);
        assertThatThrownBy(() -> service.initiateOnboarding(validRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("PAN");
    }

    @Test
        void kycValidation_isCalled_beforePersistence() {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);
            when(customerRepository.existsByMobile(anyString())).thenReturn(false);
            Customer entity = buildCustomer(null, Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
            when(customerMapper.toEntity(any())).thenReturn(entity);
            when(customerRepository.save(any())).thenReturn(entity);

            service.initiateOnboarding(validRequest());

            var inOrder = inOrder(kycValidator, customerRepository);
            inOrder.verify(kycValidator).validate(any());
            inOrder.verify(customerRepository).save(any());
        }

        @Test
        void nullPan_doesNotCheckPanDuplicate() {
        var reqNoPan = new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, "alice@example.com", "+447700900001", "British",
                null, "GB12345", null,                          // passport, no PAN
            Customer.IdDocumentType.PASSPORT, LocalDate.of(2030, 12, 31),
            new AddressRequest("123 St", null, "London", "England", "EC1", "GBR"),
            null, null, null, null, null);
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByMobile(anyString())).thenReturn(false);
        Customer entity = buildCustomer(null, Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        when(customerMapper.toEntity(any())).thenReturn(entity);
        when(customerRepository.save(any())).thenReturn(entity);

        service.initiateOnboarding(reqNoPan);

        verify(customerRepository, never()).existsByPanNumber(any());
        }
    }

    // ─── updateKycStatus ──────────────────────────────────────────────────────

    @Nested @DisplayName("updateKycStatus")
    class UpdateKycStatus {

    @Test
        void verifyKyc_setsTimestampAndOnboardingStatus() {
        Customer c = buildCustomer("CUST-001", Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
        when(customerRepository.save(any())).thenReturn(c);
        when(customerMapper.toResponse(any())).thenReturn(dummyResponse(c));

        service.updateKycStatus(new KycUpdateRequest("CUST-001", Customer.KycStatus.VERIFIED, null));

        assertThat(c.getKycStatus()).isEqualTo(Customer.KycStatus.VERIFIED);
        assertThat(c.getKycVerifiedAt()).isNotNull();
        assertThat(c.getOnboardingStatus()).isEqualTo(Customer.OnboardingStatus.KYC_VERIFIED);
        verify(kycValidator).screenForAml(c);
        verify(notificationService).sendKycApprovedEmail(eq("alice@example.com"), anyString());
    }

    @Test
        void rejectKyc_setsRejectionReasonAndStatus() {
        Customer c = buildCustomer("CUST-001", Customer.KycStatus.UNDER_REVIEW, Customer.OnboardingStatus.DOCUMENTS_SUBMITTED);
        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
        when(customerRepository.save(any())).thenReturn(c);
        when(customerMapper.toResponse(any())).thenReturn(dummyResponse(c));

        service.updateKycStatus(new KycUpdateRequest("CUST-001", Customer.KycStatus.REJECTED, "Document unclear"));

        assertThat(c.getKycStatus()).isEqualTo(Customer.KycStatus.REJECTED);
        assertThat(c.getKycRejectionReason()).isEqualTo("Document unclear");
        assertThat(c.getOnboardingStatus()).isEqualTo(Customer.OnboardingStatus.REJECTED);
        verify(notificationService).sendKycRejectedEmail(anyString(), anyString(), eq("Document unclear"));
        verify(notificationService, never()).sendKycApprovedEmail(anyString(), anyString());
    }

    @Test
        void underReview_doesNotFireApproveOrRejectNotification() {
        Customer c = buildCustomer("CUST-001", Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
        when(customerRepository.save(any())).thenReturn(c);
        when(customerMapper.toResponse(any())).thenReturn(dummyResponse(c));

        service.updateKycStatus(new KycUpdateRequest("CUST-001", Customer.KycStatus.UNDER_REVIEW, null));

        verify(notificationService, never()).sendKycApprovedEmail(anyString(), anyString());
        verify(notificationService, never()).sendKycRejectedEmail(anyString(), anyString(), anyString());
    }

    @Test
        void customerNotFound_throwsCustomerNotFoundException() {
        when(customerRepository.findById("GHOST")).thenReturn(Optional.empty());
        assertThatThrownBy(() ->
            service.updateKycStatus(new KycUpdateRequest("GHOST", Customer.KycStatus.VERIFIED, null)))
                .isInstanceOf(CustomerNotFoundException.class);
        }
    }

    // ─── completeOnboarding ───────────────────────────────────────────────────

    @Nested @DisplayName("completeOnboarding")
    class CompleteOnboarding {

        @Test
        void kycVerifiedCustomer_completesAndNotifies() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.KYC_VERIFIED);
            when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
            when(customerRepository.save(any())).thenReturn(c);
            when(customerMapper.toResponse(any())).thenReturn(dummyResponse(c));

            service.completeOnboarding("CUST-001");

            assertThat(c.getOnboardingStatus()).isEqualTo(Customer.OnboardingStatus.COMPLETED);
            assertThat(c.getOnboardingCompletedAt()).isNotNull();
            verify(notificationService).sendOnboardingCompleteEmail(anyString(), anyString());
        }

        @Test
        void kycPending_throwsKycNotApprovedException() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.PENDING, Customer.OnboardingStatus.INITIATED);
            when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
            assertThatThrownBy(() -> service.completeOnboarding("CUST-001"))
                    .isInstanceOf(KycNotApprovedException.class)
                    .hasMessageContaining("CUST-001");
        }

        @Test
        void kycRejected_throwsKycNotApprovedException() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.REJECTED, Customer.OnboardingStatus.REJECTED);
            when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
            assertThatThrownBy(() -> service.completeOnboarding("CUST-001"))
                    .isInstanceOf(KycNotApprovedException.class);
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("getCustomer / getCustomerByEmail")
    class Queries {

        @Test
        void getCustomer_found_returnsResponse() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.COMPLETED);
            when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(c));
            when(customerMapper.toResponse(c)).thenReturn(dummyResponse(c));
            assertThat(service.getCustomer("CUST-001").customerId()).isEqualTo("CUST-001");
        }

        @Test
        void getCustomer_notFound_throwsCustomerNotFoundException() {
            when(customerRepository.findById("GHOST")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCustomer("GHOST"))
                    .isInstanceOf(CustomerNotFoundException.class);
        }

        @Test
        void getCustomerByEmail_found_returnsResponse() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.VERIFIED, Customer.OnboardingStatus.COMPLETED);
            when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(c));
            when(customerMapper.toResponse(c)).thenReturn(dummyResponse(c));
            assertThat(service.getCustomerByEmail("alice@example.com").email()).isEqualTo("alice@example.com");
        }

        @Test
        void getCustomerByEmail_notFound_throwsCustomerNotFoundException() {
            when(customerRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCustomerByEmail("ghost@x.com"))
                    .isInstanceOf(CustomerNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPendingKycCustomers")
    class PendingKyc {

        @Test
        void returnsPagedSummaries() {
            Customer c = buildCustomer("CUST-001", Customer.KycStatus.UNDER_REVIEW, Customer.OnboardingStatus.DOCUMENTS_SUBMITTED);
            var page = new PageImpl<>(List.of(c));
            when(customerRepository.findPendingKycCustomers(any())).thenReturn(page);
            when(customerMapper.toSummary(c)).thenReturn(dummySummary(c));

            var result = service.getPendingKycCustomers(PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).customerId()).isEqualTo("CUST-001");
        }

        @Test
        void emptyQueue_returnsEmptyPage() {
            when(customerRepository.findPendingKycCustomers(any())).thenReturn(new PageImpl<>(List.of()));
            assertThat(service.getPendingKycCustomers(PageRequest.of(0, 10)).getTotalElements()).isZero();
        }
    }
}
