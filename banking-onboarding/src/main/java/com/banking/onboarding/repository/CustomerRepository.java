package com.banking.onboarding.repository;

import com.banking.onboarding.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String>,
                                            JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByMobile(String mobile);
    Optional<Customer> findByPanNumber(String panNumber);

    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByPanNumber(String panNumber);

    Page<Customer> findByOnboardingStatus(Customer.OnboardingStatus status, Pageable pageable);
    Page<Customer> findByKycStatus(Customer.KycStatus kycStatus, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.kycStatus = 'PENDING' OR c.kycStatus = 'UNDER_REVIEW' ORDER BY c.createdAt ASC")
    Page<Customer> findPendingKycCustomers(Pageable pageable);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.onboardingStatus = :status")
    long countByOnboardingStatus(Customer.OnboardingStatus status);
}
