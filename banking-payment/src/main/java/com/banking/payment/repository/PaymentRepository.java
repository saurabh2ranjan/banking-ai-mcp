package com.banking.payment.repository;

import com.banking.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByReferenceNumber(String referenceNumber);

    Page<Payment> findBySourceAccountIdOrderByInitiatedAtDesc(String accountId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.sourceAccountId = :accountId " +
           "AND p.initiatedAt >= :since ORDER BY p.initiatedAt DESC")
    List<Payment> findRecentBySourceAccount(String accountId, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.sourceAccountId = :accountId " +
           "AND p.status IN ('COMPLETED', 'PROCESSING') " +
           "AND p.initiatedAt >= :since")
    BigDecimal sumAmountSince(String accountId, LocalDateTime since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.sourceAccountId = :accountId " +
           "AND p.initiatedAt >= :since")
    long countTransactionsSince(String accountId, LocalDateTime since);

    @Query("SELECT MAX(p.amount) FROM Payment p WHERE p.sourceAccountId = :accountId " +
           "AND p.initiatedAt >= :since")
    BigDecimal maxAmountSince(String accountId, LocalDateTime since);

    Page<Payment> findByCustomerIdOrderByInitiatedAtDesc(String customerId, Pageable pageable);

    List<Payment> findByStatus(Payment.PaymentStatus status);
}
