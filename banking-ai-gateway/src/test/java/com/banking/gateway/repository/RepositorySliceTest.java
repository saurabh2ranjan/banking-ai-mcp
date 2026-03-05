package com.banking.gateway.repository;

import com.banking.account.domain.Account;
import com.banking.account.repository.AccountRepository;
import com.banking.payment.domain.Payment;
import com.banking.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA repository slice tests.
 * Uses @DataJpaTest to load only JPA/repository layer with H2.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Repository Slice Tests — Account and Payment")
class RepositorySliceTest {

    @Autowired AccountRepository accountRepository;
    @Autowired PaymentRepository  paymentRepository;

    // ─── Account helpers ──────────────────────────────────────────────────────

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

    // ─── Payment helpers ──────────────────────────────────────────────────────

    private Payment buildPayment(String id, String ref, String srcAcc,
                                  BigDecimal amount, Payment.PaymentStatus status,
                                  LocalDateTime initiatedAt) {
        return Payment.builder()
                .paymentId(id)
                .referenceNumber(ref)
                .customerId("CUST-001")
                .sourceAccountId(srcAcc)
                .destinationAccountId("ACC-DST")
                .amount(amount)
                .currency("GBP")
                .paymentType(Payment.PaymentType.IMPS)
                .status(status)
                .initiatedAt(initiatedAt != null ? initiatedAt : LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // AccountRepository tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AccountRepository")
    class AccountRepositoryTests {

        @BeforeEach
        void setUp() {
            accountRepository.deleteAll();
        }

        @Test
        @DisplayName("save and findById round-trip")
        void saveAndFindById() {
            Account a = accountRepository.save(buildAccount("ACC-001", "CUST-001",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE,
                    new BigDecimal("10000")));
            Optional<Account> found = accountRepository.findById("ACC-001");
            assertThat(found).isPresent();
            assertThat(found.get().getBalance()).isEqualByComparingTo("10000.00");
            assertThat(found.get().getCurrency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("findByCustomerIdOrderByCreatedAtDesc returns all accounts for customer")
        void findByCustomerId_returnsAll() {
            accountRepository.save(buildAccount("ACC-A1", "CUST-A",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));
            accountRepository.save(buildAccount("ACC-A2", "CUST-A",
                    Account.AccountType.CURRENT, Account.AccountStatus.ACTIVE, new BigDecimal("15000")));
            accountRepository.save(buildAccount("ACC-B1", "CUST-B",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("2000")));

            List<Account> custA = accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-A");
            assertThat(custA).hasSize(2);
            assertThat(custA).extracting("customerId").containsOnly("CUST-A");

            List<Account> custB = accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-B");
            assertThat(custB).hasSize(1);
        }

        @Test
        @DisplayName("findByCustomerIdOrderByCreatedAtDesc returns empty for unknown customer")
        void findByCustomerId_emptyForUnknownCustomer() {
            List<Account> result = accountRepository.findByCustomerIdOrderByCreatedAtDesc("CUST-GHOST");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByAccountNumber returns correct account")
        void findByAccountNumber() {
            accountRepository.save(buildAccount("ACC-NUM1", "CUST-001",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));

            Optional<Account> found = accountRepository.findByAccountNumber("GBNUM1");
            assertThat(found).isPresent();
            assertThat(found.get().getAccountId()).isEqualTo("ACC-NUM1");
        }

        @Test
        @DisplayName("findByAccountNumber returns empty for unknown account number")
        void findByAccountNumber_emptyForUnknown() {
            assertThat(accountRepository.findByAccountNumber("GB-GHOST-0001")).isEmpty();
        }

        @Test
        @DisplayName("existsByAccountNumber returns true for existing and false for missing")
        void existsByAccountNumber() {
            accountRepository.save(buildAccount("ACC-EX1", "CUST-001",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
            assertThat(accountRepository.existsByAccountNumber("GBEX1")).isTrue();
            assertThat(accountRepository.existsByAccountNumber("GB-GHOST")).isFalse();
        }

        @Test
        @DisplayName("findActiveByCustomerId only returns ACTIVE accounts")
        void findActiveByCustomerId() {
            accountRepository.save(buildAccount("ACC-ACT", "CUST-C",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));
            accountRepository.save(buildAccount("ACC-BLK", "CUST-C",
                    Account.AccountType.CURRENT, Account.AccountStatus.BLOCKED, new BigDecimal("3000")));

            List<Account> active = accountRepository.findActiveByCustomerId("CUST-C");
            assertThat(active).hasSize(1);
            assertThat(active.get(0).getAccountId()).isEqualTo("ACC-ACT");
        }

        @Test
        @DisplayName("findByCustomerIdAndStatus filters correctly by status")
        void findByCustomerIdAndStatus() {
            accountRepository.save(buildAccount("ACC-S1", "CUST-D",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
            accountRepository.save(buildAccount("ACC-S2", "CUST-D",
                    Account.AccountType.CURRENT, Account.AccountStatus.BLOCKED, BigDecimal.TEN));
            accountRepository.save(buildAccount("ACC-S3", "CUST-D",
                    Account.AccountType.SALARY, Account.AccountStatus.ACTIVE, BigDecimal.TEN));

            List<Account> active = accountRepository.findByCustomerIdAndStatus("CUST-D", Account.AccountStatus.ACTIVE);
            assertThat(active).hasSize(2);

            List<Account> blocked = accountRepository.findByCustomerIdAndStatus("CUST-D", Account.AccountStatus.BLOCKED);
            assertThat(blocked).hasSize(1);
            assertThat(blocked.get(0).getAccountId()).isEqualTo("ACC-S2");
        }

        @Test
        @DisplayName("countActiveAccountsByCustomer returns correct count")
        void countActiveAccountsByCustomer() {
            accountRepository.save(buildAccount("ACC-CNT1", "CUST-E",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
            accountRepository.save(buildAccount("ACC-CNT2", "CUST-E",
                    Account.AccountType.CURRENT, Account.AccountStatus.ACTIVE, BigDecimal.TEN));
            accountRepository.save(buildAccount("ACC-CNT3", "CUST-E",
                    Account.AccountType.SALARY, Account.AccountStatus.CLOSED, BigDecimal.TEN));  // CLOSED = not counted

            long count = accountRepository.countActiveAccountsByCustomer("CUST-E");
            assertThat(count).isEqualTo(2);  // CLOSED excluded
        }

        @Test
        @DisplayName("Balance changes are persisted correctly")
        void balanceChangePersisted() {
            Account a = accountRepository.save(buildAccount("ACC-BAL", "CUST-F",
                    Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE, new BigDecimal("5000")));

            a.credit(new BigDecimal("2500"));
            accountRepository.save(a);

            Account reloaded = accountRepository.findById("ACC-BAL").orElseThrow();
            assertThat(reloaded.getBalance()).isEqualByComparingTo("7500.00");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PaymentRepository tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PaymentRepository")
    class PaymentRepositoryTests {

        @BeforeEach
        void setUp() {
            paymentRepository.deleteAll();
        }

        @Test
        @DisplayName("save and findById round-trip")
        void saveAndFindById() {
            Payment p = paymentRepository.save(buildPayment("PAY-001", "REF-001",
                    "ACC-SRC", new BigDecimal("500"), Payment.PaymentStatus.PENDING_FRAUD_CHECK, null));
            Optional<Payment> found = paymentRepository.findById("PAY-001");
            assertThat(found).isPresent();
            assertThat(found.get().getAmount()).isEqualByComparingTo("500.00");
            assertThat(found.get().getStatus()).isEqualTo(Payment.PaymentStatus.PENDING_FRAUD_CHECK);
        }

        @Test
        @DisplayName("findByReferenceNumber returns correct payment")
        void findByReferenceNumber() {
            paymentRepository.save(buildPayment("PAY-REF", "IMPS-UNIQUE-001",
                    "ACC-SRC", BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
            Optional<Payment> found = paymentRepository.findByReferenceNumber("IMPS-UNIQUE-001");
            assertThat(found).isPresent();
            assertThat(found.get().getPaymentId()).isEqualTo("PAY-REF");
        }

        @Test
        @DisplayName("findByReferenceNumber returns empty for unknown reference")
        void findByReferenceNumber_emptyForUnknown() {
            assertThat(paymentRepository.findByReferenceNumber("REF-GHOST")).isEmpty();
        }

        @Test
        @DisplayName("findBySourceAccountIdOrderByInitiatedAtDesc returns paged results in desc order")
        void findBySourceAccount_pagedAndOrdered() {
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
                    new BigDecimal("999"), Payment.PaymentStatus.COMPLETED, t1));  // different account

            Page<Payment> page = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(
                    "ACC-X", PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(3);
            // Most recent first
            assertThat(page.getContent().get(0).getPaymentId()).isEqualTo("PAY-P3");
            assertThat(page.getContent().get(2).getPaymentId()).isEqualTo("PAY-P1");
        }

        @Test
        @DisplayName("findBySourceAccountIdOrderByInitiatedAtDesc — pagination works")
        void findBySourceAccount_pagination() {
            for (int i = 0; i < 5; i++) {
                paymentRepository.save(buildPayment("PAY-PG" + i, "REF-PG" + i, "ACC-PAGE",
                        BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
            }

            Page<Payment> firstPage  = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(
                    "ACC-PAGE", PageRequest.of(0, 2));
            Page<Payment> secondPage = paymentRepository.findBySourceAccountIdOrderByInitiatedAtDesc(
                    "ACC-PAGE", PageRequest.of(1, 2));

            assertThat(firstPage.getTotalElements()).isEqualTo(5);
            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(secondPage.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("findRecentBySourceAccount filters by time window correctly")
        void findRecentBySourceAccount_filtersByTime() {
            LocalDateTime recent    = LocalDateTime.now().minusMinutes(30);
            LocalDateTime old       = LocalDateTime.now().minusHours(3);
            LocalDateTime cutoff    = LocalDateTime.now().minusHours(1);

            paymentRepository.save(buildPayment("PAY-R1", "REF-R1", "ACC-R",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, recent));
            paymentRepository.save(buildPayment("PAY-R2", "REF-R2", "ACC-R",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, old));   // older than cutoff

            List<Payment> recent1h = paymentRepository.findRecentBySourceAccount("ACC-R", cutoff);
            assertThat(recent1h).hasSize(1);
            assertThat(recent1h.get(0).getPaymentId()).isEqualTo("PAY-R1");
        }

        @Test
        @DisplayName("sumAmountSince returns total of COMPLETED and PROCESSING payments")
        void sumAmountSince_onlyCompletedAndProcessing() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

            paymentRepository.save(buildPayment("PAY-S1", "REF-S1", "ACC-S",
                    new BigDecimal("1000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-S2", "REF-S2", "ACC-S",
                    new BigDecimal("500"), Payment.PaymentStatus.PROCESSING, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-S3", "REF-S3", "ACC-S",
                    new BigDecimal("2000"), Payment.PaymentStatus.FAILED, LocalDateTime.now()));  // excluded

            BigDecimal total = paymentRepository.sumAmountSince("ACC-S", cutoff);
            assertThat(total).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("sumAmountSince returns 0 when no qualifying payments")
        void sumAmountSince_returnsZeroWhenEmpty() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            BigDecimal total = paymentRepository.sumAmountSince("ACC-NONE", cutoff);
            assertThat(total).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("countTransactionsSince counts all payment statuses")
        void countTransactionsSince() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

            paymentRepository.save(buildPayment("PAY-CT1", "REF-CT1", "ACC-CT",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-CT2", "REF-CT2", "ACC-CT",
                    BigDecimal.TEN, Payment.PaymentStatus.FAILED, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-CT3", "REF-CT3", "ACC-CT",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED,
                    LocalDateTime.now().minusHours(2)));  // outside window

            long count = paymentRepository.countTransactionsSince("ACC-CT", cutoff);
            assertThat(count).isEqualTo(2);  // CT1 + CT2, CT3 is outside window
        }

        @Test
        @DisplayName("maxAmountSince returns largest payment amount")
        void maxAmountSince_returnsLargest() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

            paymentRepository.save(buildPayment("PAY-MX1", "REF-MX1", "ACC-MX",
                    new BigDecimal("1000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-MX2", "REF-MX2", "ACC-MX",
                    new BigDecimal("5000"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));
            paymentRepository.save(buildPayment("PAY-MX3", "REF-MX3", "ACC-MX",
                    new BigDecimal("250"), Payment.PaymentStatus.COMPLETED, LocalDateTime.now()));

            BigDecimal max = paymentRepository.maxAmountSince("ACC-MX", cutoff);
            assertThat(max).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("maxAmountSince returns null when no payments exist")
        void maxAmountSince_nullWhenEmpty() {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            BigDecimal max = paymentRepository.maxAmountSince("ACC-NONE", cutoff);
            assertThat(max).isNull();
        }

        @Test
        @DisplayName("findByStatus returns only payments in requested status")
        void findByStatus() {
            paymentRepository.save(buildPayment("PAY-ST1", "REF-ST1", "ACC-T",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));
            paymentRepository.save(buildPayment("PAY-ST2", "REF-ST2", "ACC-T",
                    BigDecimal.TEN, Payment.PaymentStatus.FRAUD_HOLD, null));
            paymentRepository.save(buildPayment("PAY-ST3", "REF-ST3", "ACC-T",
                    BigDecimal.TEN, Payment.PaymentStatus.FRAUD_HOLD, null));

            List<Payment> holds = paymentRepository.findByStatus(Payment.PaymentStatus.FRAUD_HOLD);
            assertThat(holds).hasSize(2);
            assertThat(holds).extracting("paymentId").containsExactlyInAnyOrder("PAY-ST2", "PAY-ST3");

            List<Payment> completed = paymentRepository.findByStatus(Payment.PaymentStatus.COMPLETED);
            assertThat(completed).hasSize(1);
        }

        @Test
        @DisplayName("findByCustomerIdOrderByInitiatedAtDesc returns payments for customer")
        void findByCustomerId() {
            paymentRepository.save(buildPayment("PAY-CUS1", "REF-CUS1", "ACC-C1",
                    BigDecimal.TEN, Payment.PaymentStatus.COMPLETED, null));

            Page<Payment> page = paymentRepository.findByCustomerIdOrderByInitiatedAtDesc(
                    "CUST-001", PageRequest.of(0, 10, Sort.by("initiatedAt").descending()));
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }
}
