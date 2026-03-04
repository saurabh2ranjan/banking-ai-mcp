package com.banking.account.repository;

import com.banking.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Account> findByCustomerIdAndStatus(String customerId, Account.AccountStatus status);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.customerId = :customerId AND a.status = 'ACTIVE'")
    List<Account> findActiveByCustomerId(String customerId);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.customerId = :customerId AND a.status != 'CLOSED'")
    long countActiveAccountsByCustomer(String customerId);
}
