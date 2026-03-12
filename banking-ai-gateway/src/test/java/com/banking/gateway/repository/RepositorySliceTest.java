package com.banking.gateway.repository;

import com.banking.account.domain.Account;
import com.banking.account.repository.AccountRepository;
import com.banking.payment.domain.Payment;
import com.banking.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RepositorySliceTest {

    @Autowired AccountRepository accountRepository;
    @Autowired PaymentRepository  paymentRepository;

    @TestConfiguration
    @EnableJpaRepositories(basePackages = "com.banking")
    @EntityScan(basePackages = "com.banking")
    static class TestJpaConfig { }

    @BeforeEach
    void cleanUp() {
        paymentRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── account helpers ────────────────────────────────────────────────────────

    private Account buildAccount(String id, String customerId, Account.AccountType type,
                                  Account.AccountStatus status, BigDecimal balance) {
        return Account.builder()
                .accountId(id)
                .accountNumber("GB" + id.replace("ACC-", ""))
                .customerId(customerId)
                .accountType(type)
                .status(status)
                .balance(balance)
                .availableBalance(balance)
                .holdAmount(BigDecimal.ZERO)
                .minimumBalance(new BigDecimal("500"))
                .currency("GBP")
                .interestRate(new BigDecimal("0.035"))
                .openedDate(LocalDate.now())
                .build();
    }

    // ── payment helpers ────────────────────────────────────────────────────────

    private Payment buildPayment(String id, String ref, String srcAcc,
                                  BigDecimal amount, Payment.PaymentStatus status,
                                  LocalDateTime initiatedAt) {
        return Payment.builder()
                .paymentId(id).referenceNumber(ref)
                .customerId("CUST-001")
                .sourceAccountId(srcAcc).destinationAccountId("ACC-DST")
                .amount(amount).currency("GBP")
                .paymentType(Payment.PaymentType.IMPS)
                .status(status)
                .initiatedAt(initiatedAt != null ? initiatedAt : LocalDateTime.now())
                .build();
    }

    // ── AccountRepository ──────────────────────────────────────────────────────

    @Test
    void accountRepository_saveAndFindById_roundTrip() {
        accountRepository.save(buildAccount("ACC-001", "CUST-001",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("10000")));

        Optional<Account> found = accountRepository.findById("ACC-001");

        assertThat(found).isPresent();
        assertThat(found.get().getBalance()).isEqualByComparingTo("10000.00");
        assertThat(found.get().getCurrency()).isEqualTo("GBP");
    }

    @Test
    void accountRepository_findByCustomerId_returnsOnlyThatCustomersAccounts() {
        accountRepository.save(buildAccount("ACC-A1", "CUST-A",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));
        accountRepository.save(buildAccount("ACC-A2", "CUST-A",
                Account.AccountType.CURRENT, Account.AccountStatus.ACTIVE, new BigDecimal("15000")));
        accountRepository.save(buildAccount("ACC-B1", "CUST-B",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("2000")));

        assertThat(accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-A")).hasSize(2)
                .extracting("customerId").containsOnly("CUST-A");
        assertThat(accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-B")).hasSize(1);
    }

    @Test
    void accountRepository_findByCustomerId_emptyForUnknownCustomer() {
        assertThat(accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-GHOST")).isEmpty();
    }

    @Test
    void accountRepository_findByAccountNumber_returnsCorrectAccount() {
        accountRepository.save(buildAccount("ACC-NUM1", "CUST-001",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));

        Optional<Account> found = accountRepository.findByAccountNumber("GBNUM1");

        assertThat(found).isPresent();
        assertThat(found.get().getAccountId()).isEqualTo("ACC-NUM1");
    }

    @Test
    void accountRepository_findByAccountNumber_emptyForUnknown() {
        assertThat(accountRepository.findByAccountNumber("GB-GHOST-0001")).isEmpty();
    }

    @Test
    void accountRepository_existsByAccountNumber_returnsTrueForExistingFalseForMissing() {
        accountRepository.save(buildAccount("ACC-EX1", "CUST-001",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));

        assertThat(accountRepository.existsByAccountNumber("GBEX1")).isTrue();
        assertThat(accountRepository.existsByAccountNumber("GB-GHOST")).isFalse();
    }

    @Test
    void accountRepository_findActiveByCustomerId_excludesNonActiveAccounts() {
        accountRepository.save(buildAccount("ACC-ACT", "CUST-C",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));
        accountRepository.save(buildAccount("ACC-BLK", "CUST-C",
                Account.AccountType.CURRENT, Account.AccountStatus.BLOCKED, new BigDecimal("3000")));

        List<Account> active = accountRepository.findActiveByCustomerId("CUST-C");

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getAccountId()).isEqualTo("ACC-ACT");
    }

    @Test
    void accountRepository_findByCustomerIdAndStatus_filtersCorrectlyByStatus() {
        accountRepository.save(buildAccount("ACC-S1", "CUST-D",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
        accountRepository.save(buildAccount("ACC-S2", "CUST-D",
                Account.AccountType.CURRENT, Account.AccountStatus.BLOCKED, BigDecimal.TEN));
        accountRepository.save(buildAccount("ACC-S3", "CUST-D",
                Account.AccountType.SALARY, Account.AccountStatus.ACTIVE, BigDecimal.TEN));

        assertThat(accountRepository.findByCustomerIdAndStatus("CUST-D", Account.AccountStatus.ACTIVE)).hasSize(2);
        List<Account> blocked = accountRepository.findByCustomerIdAndStatus("CUST-D", Account.AccountStatus.BLOCKED);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).getAccountId()).isEqualTo("ACC-S2");
    }

    @Test
    void accountRepository_countActiveAccountsByCustomer_excludesClosedAccounts() {
        accountRepository.save(buildAccount("ACC-CNT1", "CUST-E",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
        accountRepository.save(buildAccount("ACC-CNT2", "CUST-E",
                Account.AccountType.CURRENT, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
        accountRepository.save(buildAccount("ACC-CNT3", "CUST-E",
                Account.AccountType.SALARY, Account.AccountStatus.CLOSED, BigDecimal.TEN));

        assertThat(accountRepository.countActiveAccountsByCustomer("CUST-E")).isEqualTo(2);
    }

    @Test
    void accountRepository_balanceChangesArePersisted() {
        Account a = accountRepository.save(buildAccount("ACC-BAL", "CUST-F",
                Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));
        a.credit(new BigDecimal("2500"));
        accountRepository.save(a);

        assertThat(accountRepository.findById("ACC-BAL").orElseThrow().getBalance())
                .isEqualByComparingTo("7500.00");
    }

    // ── PaymentRepository ──────────────────────────────────────────────────────

    @Test
    void paymentRepository_saveAndFindById_roundTrip() {
        paymentRepository.save(buildPayment("PAY-001", "REF-001", "ACC-SRC",
                new BigDecimal("500"), Payment.PaymentStatus.PENDING_FRAUD_CHECK, null));

        Optional<Payment> found = paymentRepository.findById("PAY-001");

        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualByComparingTo("500.00");
        assertThat(found.get().getStatus()).isEqualTo(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
    }

    @Test
    void paymentRepository_findByReferenceNumber_returnsCorrectPayment() {
        paymentRepository.save(buildPayment("PAY-REF", "IMPS-UNIQUE-001", "ACC-SRC",
                BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));

        Optional<Payment> found = paymentRepository.findByReferenceNumber("IMPS-UNIQUE-001");

        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo("PAY-REF");
    }

    @Test
    void paymentRepository_findByReferenceNumber_emptyForUnknown() {
        assertThat(paymentRepository.findByReferenceNumber("REF-GHOST")).isEmpty();
    }

    @Test
    void paymentRepository_findBySourceAccount_returnsMostRecentFirst() {
        LocalDateTime t1 = LocalDateTime.now().minusHours(2);
        LocalDateTime t2 = LocalDateTime.now().minusHours(1);
        LocalDateTime t3 = LocalDateTime.now();

        paymentRepository.save(buildPayment("PAY-P1", "REF-P1", "ACC-X",
                new BigDecimal("100"), Payment.PaymentStatus.COMPLETED, t1));
        paymentRepository.save(buildPayment("PAY-P2", "REF-P2", "ACC-X",
                new BigDecimal("200"), Payment.PaymentStatus.COMPLETED, t2));
        paymentRepository.save(buildPayment("PAY-P3", "REF-P3", "ACC-X",
                new BigDecimal("300"), Payment.PaymentStatus.COMPLETED, t3));
        paymentRepository.save(buildPayment("PAY-P4", "REF-P4", "ACC-Y",
                new BigDecimal("999"), Payment.PaymentStatus.COMPLETED, t1));

        Page<Payment> page = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(
                "ACC-X", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().get(0).getPaymentId()).isEqualTo("PAY-P3");
        assertThat(page.getContent().get(2).getPaymentId()).isEqualTo("PAY-P1");
    }

    @Test
    void paymentRepository_findBySourceAccount_paginationWorks() {
        for (int i = 0; i < 5; i++) {
            paymentRepository.save(buildPayment("PAY-PG" + i, "REF-PG" + i, "ACC-PAGE",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
        }

        Page<Payment> first  = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc("ACC-PAGE", PageRequest.of(0, 2));
        Page<Payment> second = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc("ACC-PAGE", PageRequest.of(1, 2));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(2);
    }

    @Test
    void paymentRepository_findRecentBySourceAccount_filtersByTimeWindow() {
        // @PrePersist always stamps initiatedAt = now(), so backdated saves are impossible.
        // Save one payment, then verify it is included by a past cutoff and excluded by a future one.
        paymentRepository.save(buildPayment("PAY-R1", "REF-R1", "ACC-R",
                BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));

        LocalDateTime pastCutoff   = LocalDateTime.now().minusHours(1);
        LocalDateTime futureCutoff = LocalDateTime.now().plusSeconds(1);

        List<Payment> included = paymentRepository.findRecentBySourceAccount("ACC-R", pastCutoff);
        assertThat(included).hasSize(1);
        assertThat(included.get(0).getPaymentId()).isEqualTo("PAY-R1");

        List<Payment> excluded = paymentRepository.findRecentBySourceAccount("ACC-R", futureCutoff);
        assertThat(excluded).isEmpty();
    }

    @Test
    void paymentRepository_sumAmountSince_onlyIncludesCompletedAndProcessing() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        paymentRepository.save(buildPayment("PAY-S1", "REF-S1", "ACC-S",
                new BigDecimal("1000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
        paymentRepository.save(buildPayment("PAY-S2", "REF-S2", "ACC-S",
                new BigDecimal("500"), Payment.PaymentStatus.PROCESSING, LocalDateTime.now()));
        paymentRepository.save(buildPayment("PAY-S3", "REF-S3", "ACC-S",
                new BigDecimal("2000"), Payment.PaymentStatus.FAILED, LocalDateTime.now()));

        assertThat(paymentRepository.sumAmountSince("ACC-S", cutoff)).isEqualByComparingTo("1500.00");
    }

    @Test
    void paymentRepository_sumAmountSince_returnsZeroWhenNoneQualify() {
        assertThat(paymentRepository.sumAmountSince("ACC-NONE", LocalDateTime.now().minusHours(1)))
                .isEqualByComparingTo("0");
    }

    @Test
    void paymentRepository_countTransactionsSince_onlyCountsWithinWindow() {
        // @PrePersist always stamps initiatedAt = now(), so we can't backdate saves.
        // Instead: save two payments, record the cutoff, then assert both are within the window.
        // Exclusion-by-time is verified via a future cutoff that captures nothing.
        paymentRepository.save(buildPayment("PAY-CT1", "REF-CT1", "ACC-CT",
                BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
        paymentRepository.save(buildPayment("PAY-CT2", "REF-CT2", "ACC-CT",
                BigDecimal.TEN, Payment.PaymentStatus.FAILED, null));

        LocalDateTime pastCutoff   = LocalDateTime.now().minusHours(1);
        LocalDateTime futureCutoff = LocalDateTime.now().plusSeconds(1);

        assertThat(paymentRepository.countTransactionsSince("ACC-CT", pastCutoff)).isEqualTo(2);
        assertThat(paymentRepository.countTransactionsSince("ACC-CT", futureCutoff)).isZero();
    }

    @Test
    void paymentRepository_maxAmountSince_returnsLargestAmount() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        paymentRepository.save(buildPayment("PAY-MX1", "REF-MX1", "ACC-MX",
                new BigDecimal("1000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
        paymentRepository.save(buildPayment("PAY-MX2", "REF-MX2", "ACC-MX",
                new BigDecimal("5000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
        paymentRepository.save(buildPayment("PAY-MX3", "REF-MX3", "ACC-MX",
                new BigDecimal("250"),  Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));

        assertThat(paymentRepository.maxAmountSince("ACC-MX", cutoff)).isEqualByComparingTo("5000.00");
    }

    @Test
    void paymentRepository_maxAmountSince_returnsNullWhenNoPayments() {
        assertThat(paymentRepository.maxAmountSince("ACC-NONE", LocalDateTime.now().minusHours(1))).isNull();
    }

    @Test
    void paymentRepository_findByStatus_returnsOnlyMatchingStatus() {
        paymentRepository.save(buildPayment("PAY-ST1", "REF-ST1", "ACC-T",
                BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
        paymentRepository.save(buildPayment("PAY-ST2", "REF-ST2", "ACC-T",
                BigDecimal.TEN, Payment.PaymentStatus.FRAUD_HOLD, null));
        paymentRepository.save(buildPayment("PAY-ST3", "REF-ST3", "ACC-T",
                BigDecimal.TEN, Payment.PaymentStatus.FRAUD_HOLD, null));

        List<Payment> holds = paymentRepository.findByStatus(Payment.PaymentStatus.FRAUD_HOLD);
        assertThat(holds).hasSize(2)
                .extracting("paymentId").containsExactlyInAnyOrder("PAY-ST2", "PAY-ST3");

        assertThat(paymentRepository.findByStatus(Payment.PaymentStatus.COMPLETED)).hasSize(1);
    }

    @Test
    void paymentRepository_findByCustomerId_returnsPaged() {
        paymentRepository.save(buildPayment("PAY-CUS1", "REF-CUS1", "ACC-C1",
                BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));

        Page<Payment> page = paymentRepository.findByCustomerIdOrderByInitiatedAtDesc(
                "CUST-001", PageRequest.of(0, 10, Sort.by("initiatedAt").descending()));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
