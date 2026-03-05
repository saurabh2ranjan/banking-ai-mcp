package com.banking.gateway;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.exception.BankingExceptions.*;
import com.banking.fraud.domain.FraudAnalysis;
import com.banking.fraud.service.FraudDetectionService;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos.*;
import com.banking.onboarding.service.CustomerOnboardingService;
import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests for the full banking platform.
 * Runs against Spring context + in-memory H2.
 * Each test is rolled back after execution.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Banking Platform — End-to-End Integration Tests")
class BankingEndToEndIntegrationTest {

    @Autowired CustomerOnboardingService onboardingService;
    @Autowired AccountService            accountService;
    @Autowired PaymentService            paymentService;
    @Autowired FraudDetectionService     fraudDetectionService;

    // ─── Fixture helpers ──────────────────────────────────────────────────────

    private OnboardingRequest buildOnboardingRequest(String email, String mobile,
                                                      String pan, String passport) {
        return new OnboardingRequest(
            "Alice", "Johnson", LocalDate.of(1990, 5, 15),
            Customer.Gender.FEMALE, email, mobile, "British",
            pan, passport, null,
            pan != null ? Customer.IdDocumentType.PAN_CARD : Customer.IdDocumentType.PASSPORT,
            LocalDate.of(2032, 12, 31),
            new AddressRequest("123 High Street", null, "London", "England", "EC1A 1BB", "GBR"),
            Customer.EmploymentType.SALARIED, "Tech Corp Ltd",
            new BigDecimal("80000"), "GBP", "SAVINGS"
        );
    }

    private String onboardAndComplete(String email, String mobile, String pan) {
        OnboardingResponse resp = onboardingService.initiateOnboarding(
                buildOnboardingRequest(email, mobile, pan, null));
        String id = resp.customerId();
        onboardingService.updateKycStatus(new KycUpdateRequest(id, Customer.KycStatus.VERIFIED, null));
        onboardingService.completeOnboarding(id);
        return id;
    }

    private String openAccount(String customerId, Account.AccountType type,
                               BigDecimal deposit, String currency) {
        OpenAccountRequest req = new OpenAccountRequest(
                customerId, type, currency, type.name() + " Account", deposit, null, null);
        return accountService.openAccount(req).accountId();
    }

    // ─── 1. Context loads ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Spring context starts cleanly")
    void contextLoads() {
        assertThat(onboardingService).isNotNull();
        assertThat(accountService).isNotNull();
        assertThat(paymentService).isNotNull();
        assertThat(fraudDetectionService).isNotNull();
    }

    // ─── 2. Full onboarding lifecycle ────────────────────────────────────────

    @Test
    @DisplayName("Full onboarding: INITIATED → KYC VERIFIED → COMPLETED → account opened")
    void fullOnboardingWorkflow() {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                buildOnboardingRequest("alice.e2e@example.com", "+447700900111", "ABCDE1234F", null));

        assertThat(ob.customerId()).startsWith("CUST-");
        assertThat(ob.status()).isEqualTo("INITIATED");
        assertThat(ob.nextStep()).isEqualTo("SUBMIT_DOCUMENTS");

        String customerId = ob.customerId();

        CustomerResponse kyc = onboardingService.updateKycStatus(
                new KycUpdateRequest(customerId, Customer.KycStatus.VERIFIED, null));
        assertThat(kyc.kycStatus()).isEqualTo("VERIFIED");
        assertThat(kyc.onboardingStatus()).isEqualTo("KYC_VERIFIED");

        CustomerResponse completed = onboardingService.completeOnboarding(customerId);
        assertThat(completed.onboardingStatus()).isEqualTo("COMPLETED");

        OpenAccountRequest accReq = new OpenAccountRequest(
                customerId, Account.AccountType.SAVINGS, "GBP",
                "My Savings", new BigDecimal("5000"), null, null);
        AccountResponse account = accountService.openAccount(accReq);

        assertThat(account.accountId()).matches("ACC-\\d{6}-\\d{6}-\\d");
        assertThat(account.balance()).isEqualByComparingTo("5000.00");
        assertThat(account.availableBalance()).isEqualByComparingTo("5000.00");
        assertThat(account.holdAmount()).isEqualByComparingTo("0.00");
        assertThat(account.status()).isEqualTo("ACTIVE");
        assertThat(account.accountType()).isEqualTo("SAVINGS");
        assertThat(account.interestRate()).isEqualByComparingTo("0.0350");
        assertThat(account.minimumBalance()).isEqualByComparingTo("500.00");
    }

    // ─── 3. KYC rejection ────────────────────────────────────────────────────

    @Test
    @DisplayName("Rejected KYC blocks account opening")
    void kycRejectionBlocksAccountOpening() {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                buildOnboardingRequest("reject.e2e@example.com", "+447700900112", "XYZPQ5678G", null));

        CustomerResponse rejected = onboardingService.updateKycStatus(new KycUpdateRequest(
                ob.customerId(), Customer.KycStatus.REJECTED, "Document photo was blurry"));

        assertThat(rejected.kycStatus()).isEqualTo("REJECTED");

        String id = ob.customerId();
        assertThatThrownBy(() -> accountService.openAccount(
                new OpenAccountRequest(id, Account.AccountType.SAVINGS, "GBP", null, null, null, null)))
            .isInstanceOf(OnboardingException.class)
            .hasMessageContaining("KYC is not verified");
    }

    // ─── 4. Duplicate guards ──────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate email is rejected")
    void duplicateEmail_isRejected() {
        onboardingService.initiateOnboarding(
                buildOnboardingRequest("dup@example.com", "+447700900001", "ABCDE1234F", null));
        assertThatThrownBy(() ->
            onboardingService.initiateOnboarding(
                buildOnboardingRequest("dup@example.com", "+447700900002", "LMNOP5432H", null)))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("email: dup@example.com");
    }

    @Test
    @DisplayName("Duplicate PAN is rejected")
    void duplicatePan_isRejected() {
        onboardingService.initiateOnboarding(
                buildOnboardingRequest("a@example.com", "+447700900010", "AAAAA1111A", null));
        assertThatThrownBy(() ->
            onboardingService.initiateOnboarding(
                buildOnboardingRequest("b@example.com", "+447700900011", "AAAAA1111A", null)))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("PAN");
    }

    // ─── 5. Guard rails for incomplete onboarding ─────────────────────────────

    @Test
    @DisplayName("Account cannot be opened if KYC not verified")
    void accountOpeningRequiresKycVerified() {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                buildOnboardingRequest("kyc.pending@example.com", "+447700900200", "BBBBB2222B", null));
        String id = ob.customerId();
        assertThatThrownBy(() -> accountService.openAccount(
                new OpenAccountRequest(id, Account.AccountType.SAVINGS, "GBP", null, null, null, null)))
            .isInstanceOf(OnboardingException.class)
            .hasMessageContaining("KYC is not verified");
    }

    @Test
    @DisplayName("Account cannot be opened if onboarding not explicitly completed")
    void accountOpeningRequiresOnboardingComplete() {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                buildOnboardingRequest("kyc.notcomplete@example.com", "+447700900201", "CCCCC3333C", null));
        onboardingService.updateKycStatus(
                new KycUpdateRequest(ob.customerId(), Customer.KycStatus.VERIFIED, null));
        String id = ob.customerId();
        assertThatThrownBy(() -> accountService.openAccount(
                new OpenAccountRequest(id, Account.AccountType.SAVINGS, "GBP", null, null, null, null)))
            .isInstanceOf(OnboardingException.class)
            .hasMessageContaining("onboarding is not complete");
    }

    // ─── 6. Full safe payment lifecycle ──────────────────────────────────────

    @Test
    @DisplayName("Safe IMPS payment: hold applied, fraud APPROVE, balances updated correctly")
    void fullPaymentLifecycle_safePayment() {
        String aliceId = onboardAndComplete("alice.pay@example.com", "+447700901001", "ALICE1234F");
        String bobId   = onboardAndComplete("bob.pay@example.com",   "+447700901002", "BOB001234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("20000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("5000"),  "GBP");

        PaymentResponse initiated = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("2000.00"), "GBP", Payment.PaymentType.IMPS, "Office rent"));

        assertThat(initiated.status()).isEqualTo("PENDING_FRAUD_CHECK");
        assertThat(initiated.referenceNumber()).startsWith("IMPS-");

        // Hold applied
        BalanceResponse balAfterHold = accountService.getBalance(aliceAccId);
        assertThat(balAfterHold.balance()).isEqualByComparingTo("20000.00");
        assertThat(balAfterHold.availableBalance()).isEqualByComparingTo("18000.00");
        assertThat(balAfterHold.holdAmount()).isEqualByComparingTo("2000.00");

        // Fraud check
        FraudAnalysis fraud = fraudDetectionService.analysePayment(initiated.paymentId());
        assertThat(fraud.decision()).isEqualTo(FraudAnalysis.FraudDecision.APPROVE);

        // Process
        PaymentResponse processed = paymentService.processPayment(initiated.paymentId());
        assertThat(processed.status()).isEqualTo("COMPLETED");
        assertThat(processed.completedAt()).isNotNull();

        // Final balances
        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("18000.00");
        assertThat(accountService.getBalance(aliceAccId).holdAmount()).isEqualByComparingTo("0.00");
        assertThat(accountService.getBalance(bobAccId).balance()).isEqualByComparingTo("7000.00");
    }

    // ─── 7. Insufficient funds ────────────────────────────────────────────────

    @Test
    @DisplayName("Payment rejected when source account has insufficient funds")
    void payment_insufficientFunds() {
        String aliceId = onboardAndComplete("alice.insuf@example.com", "+447700901010", "ALICB1234F");
        String bobId   = onboardAndComplete("bob.insuf@example.com",   "+447700901011", "BOBB01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.SAVINGS, new BigDecimal("1000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.SAVINGS, new BigDecimal("500"),  "GBP");

        assertThatThrownBy(() -> paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("5000.00"), "GBP", Payment.PaymentType.IMPS, "too much")))
            .isInstanceOf(InsufficientFundsException.class);

        // Balance completely unchanged
        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("1000.00");
        assertThat(accountService.getBalance(aliceAccId).holdAmount()).isEqualByComparingTo("0.00");
    }

    // ─── 8. Payment reversal ─────────────────────────────────────────────────

    @Test
    @DisplayName("Completed payment can be reversed — money returns to source")
    void paymentReversal() {
        String aliceId = onboardAndComplete("alice.rev@example.com", "+447700902001", "ALICC1234F");
        String bobId   = onboardAndComplete("bob.rev@example.com",   "+447700902002", "BOBC01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("10000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("500"),   "GBP");

        PaymentResponse pay = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("3000.00"), "GBP", Payment.PaymentType.IMPS, "test payment"));
        paymentService.processPayment(pay.paymentId());

        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("7000.00");
        assertThat(accountService.getBalance(bobAccId).balance()).isEqualByComparingTo("3500.00");

        PaymentResponse reversal = paymentService.reversePayment(pay.paymentId(), "Customer dispute");
        assertThat(reversal.status()).isEqualTo("COMPLETED");
        assertThat(reversal.sourceAccountId()).isEqualTo(bobAccId);
        assertThat(reversal.destinationAccountId()).isEqualTo(aliceAccId);
        assertThat(reversal.referenceNumber()).endsWith("-REV");

        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("10000.00");
        assertThat(accountService.getBalance(bobAccId).balance()).isEqualByComparingTo("500.00");

        assertThat(paymentService.getPayment(pay.paymentId()).status()).isEqualTo("REVERSED");
    }

    @Test
    @DisplayName("Cannot reverse a non-completed (e.g. PENDING_FRAUD_CHECK) payment")
    void reversal_nonCompletedPayment_throwsPaymentException() {
        String aliceId = onboardAndComplete("alice.rev2@example.com", "+447700902010", "ALICD1234F");
        String bobId   = onboardAndComplete("bob.rev2@example.com",   "+447700902011", "BOBD01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("10000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("1000"),  "GBP");

        PaymentResponse pay = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("500.00"), "GBP", Payment.PaymentType.NEFT, "pending"));

        String paymentId = pay.paymentId();
        assertThatThrownBy(() -> paymentService.reversePayment(paymentId, "reason"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("completed payments");
    }

    // ─── 9. Fraud hold workflow ───────────────────────────────────────────────

    @Test
    @DisplayName("High-value SWIFT triggers fraud hold; hold released when payment fails")
    void fraudHoldWorkflow() {
        String aliceId = onboardAndComplete("alice.fraud@example.com", "+447700903001", "ALICF1234F");
        String bobId   = onboardAndComplete("bob.fraud@example.com",   "+447700903002", "BOBF01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("500000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("1000"),   "GBP");

        PaymentResponse pay = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("150000.00"), "GBP", Payment.PaymentType.SWIFT, "International"));

        FraudAnalysis fraud = fraudDetectionService.analysePayment(pay.paymentId());
        assertThat(fraud.fraudScore()).isGreaterThanOrEqualTo(0.40);
        assertThat(fraud.triggeredRules()).anyMatch(r -> r.contains("HIGH_VALUE"));
        assertThat(fraud.triggeredRules()).anyMatch(r -> r.contains("INTERNATIONAL_WIRE"));
        assertThat(fraud.decision()).isIn(
                FraudAnalysis.FraudDecision.HOLD_FOR_REVIEW, FraudAnalysis.FraudDecision.BLOCK);

        paymentService.holdForFraud(pay.paymentId(), fraud.fraudScore(),
                Payment.FraudRiskLevel.HIGH, "Flagged: high-value SWIFT");

        // Hold still in place
        assertThat(accountService.getBalance(aliceAccId).holdAmount()).isEqualByComparingTo("150000.00");

        // Cannot process from FRAUD_HOLD
        String payId = pay.paymentId();
        assertThatThrownBy(() -> paymentService.processPayment(payId))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("FRAUD_HOLD");

        // Fail payment — releases hold
        paymentService.failPayment(payId, "Declined after fraud review");
        BalanceResponse finalBal = accountService.getBalance(aliceAccId);
        assertThat(finalBal.holdAmount()).isEqualByComparingTo("0.00");
        assertThat(finalBal.balance()).isEqualByComparingTo("500000.00");
    }

    // ─── 10. Block / unblock ──────────────────────────────────────────────────

    @Test
    @DisplayName("Blocked account rejects debit/credit; unblock restores access")
    void blockAndUnblockAccount() {
        String aliceId = onboardAndComplete("alice.block@example.com", "+447700904001", "ALICG1234F");
        String aliceAccId = openAccount(aliceId, Account.AccountType.SAVINGS, new BigDecimal("10000"), "GBP");

        accountService.blockAccount(aliceAccId, "Suspected fraud");
        assertThat(accountService.getAccount(aliceAccId).status()).isEqualTo("BLOCKED");

        assertThatThrownBy(() -> accountService.debitAccount(aliceAccId, BigDecimal.TEN))
                .isInstanceOf(AccountInactiveException.class);
        assertThatThrownBy(() -> accountService.creditAccount(aliceAccId, BigDecimal.TEN))
                .isInstanceOf(AccountInactiveException.class);

        accountService.unblockAccount(aliceAccId);
        assertThat(accountService.getAccount(aliceAccId).status()).isEqualTo("ACTIVE");

        accountService.creditAccount(aliceAccId, new BigDecimal("500"));
        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("10500.00");
    }

    // ─── 11. Multiple accounts per customer ───────────────────────────────────

    @Test
    @DisplayName("One customer can hold multiple accounts with correct type-specific defaults")
    void customerCanHoldMultipleAccounts() {
        String aliceId = onboardAndComplete("alice.multi@example.com", "+447700905001", "ALICH1234F");

        String savingsId = openAccount(aliceId, Account.AccountType.SAVINGS, new BigDecimal("5000"), "GBP");
        String currentId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("10000"), "GBP");
        String salaryId  = openAccount(aliceId, Account.AccountType.SALARY,  new BigDecimal("3000"), "GBP");

        var accounts = accountService.getCustomerAccounts(aliceId);
        assertThat(accounts).hasSize(3);
        assertThat(accounts).extracting("accountType")
                .containsExactlyInAnyOrder("SAVINGS", "CURRENT", "SALARY");

        assertThat(accountService.getAccount(savingsId).interestRate()).isEqualByComparingTo("0.0350");
        assertThat(accountService.getAccount(currentId).interestRate()).isEqualByComparingTo("0.0000");
    }

    // ─── 12. Daily spending summary ───────────────────────────────────────────

    @Test
    @DisplayName("Daily spending summary reflects correct totals after multiple payments")
    void dailySpendingSummary_reflectsPayments() {
        String aliceId = onboardAndComplete("alice.daily@example.com", "+447700906001", "ALICI1234F");
        String bobId   = onboardAndComplete("bob.daily@example.com",   "+447700906002", "BOBG01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("50000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("1000"),  "GBP");

        DailySpendingSummary empty = paymentService.getDailySpendingSummary(aliceAccId);
        assertThat(empty.totalSpentToday()).isEqualByComparingTo("0.00");
        assertThat(empty.transactionCount()).isZero();

        PaymentResponse p1 = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId, new BigDecimal("1000"), "GBP", Payment.PaymentType.IMPS, "p1"));
        paymentService.processPayment(p1.paymentId());

        PaymentResponse p2 = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId, new BigDecimal("2000"), "GBP", Payment.PaymentType.IMPS, "p2"));
        paymentService.processPayment(p2.paymentId());

        DailySpendingSummary summary = paymentService.getDailySpendingSummary(aliceAccId);
        assertThat(summary.totalSpentToday()).isEqualByComparingTo("3000.00");
        assertThat(summary.transactionCount()).isEqualTo(2);
        assertThat(summary.averageTransactionSize()).isEqualByComparingTo("1500.00");
        assertThat(summary.largestTransaction()).isEqualByComparingTo("2000.00");
    }

    // ─── 13. Customer queries ─────────────────────────────────────────────────

    @Test
    @DisplayName("Customer can be retrieved by ID and by email with correct field values")
    void customerQueries_byIdAndEmail() {
        OnboardingResponse ob = onboardingService.initiateOnboarding(
                buildOnboardingRequest("query.test@example.com", "+447700907001", "QUERYA1234F", null));

        CustomerResponse byId = onboardingService.getCustomer(ob.customerId());
        assertThat(byId.email()).isEqualTo("query.test@example.com");
        assertThat(byId.kycStatus()).isEqualTo("PENDING");
        assertThat(byId.fullName()).isEqualTo("Alice Johnson");

        CustomerResponse byEmail = onboardingService.getCustomerByEmail("query.test@example.com");
        assertThat(byEmail.customerId()).isEqualTo(ob.customerId());
    }

    @Test
    @DisplayName("Fetching unknown customer throws CustomerNotFoundException")
    void getCustomer_unknown_throwsNotFoundException() {
        assertThatThrownBy(() -> onboardingService.getCustomer("CUST-GHOST-999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("Fetching unknown payment throws PaymentNotFoundException")
    void getPayment_unknown_throwsNotFoundException() {
        assertThatThrownBy(() -> paymentService.getPayment("PAY-NONEXISTENT"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ─── 14. Fraud analysis: safe payment scores LOW ──────────────────────────

    @Test
    @DisplayName("Small domestic IMPS payment receives APPROVE decision with low score")
    void fraudAnalysis_smallPayment_isApproved() {
        String aliceId = onboardAndComplete("alice.safe@example.com", "+447700908001", "ALICEJ1234F");
        String bobId   = onboardAndComplete("bob.safe@example.com",   "+447700908002", "BOBH01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("10000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("500"),   "GBP");

        PaymentResponse pay = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("250.00"), "GBP", Payment.PaymentType.IMPS, "coffee"));

        FraudAnalysis fraud = fraudDetectionService.analysePayment(pay.paymentId());
        assertThat(fraud.fraudScore()).isLessThan(0.40);
        assertThat(fraud.decision()).isEqualTo(FraudAnalysis.FraudDecision.APPROVE);
        assertThat(fraud.paymentId()).isEqualTo(pay.paymentId());
        assertThat(fraud.analysedAt()).isNotNull();
    }

    // ─── 15. Pending KYC queue ────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingKycCustomers returns customers with UNDER_REVIEW status")
    void pendingKycQueue_returnsUnderReviewCustomers() {
        OnboardingResponse ob1 = onboardingService.initiateOnboarding(
                buildOnboardingRequest("pending1@example.com", "+447700909001", "PENDA01234F", null));
        OnboardingResponse ob2 = onboardingService.initiateOnboarding(
                buildOnboardingRequest("pending2@example.com", "+447700909002", "PENDB01234G", null));

        onboardingService.updateKycStatus(
                new KycUpdateRequest(ob1.customerId(), Customer.KycStatus.UNDER_REVIEW, null));
        onboardingService.updateKycStatus(
                new KycUpdateRequest(ob2.customerId(), Customer.KycStatus.UNDER_REVIEW, null));

        var page = onboardingService.getPendingKycCustomers(PageRequest.of(0, 20));
        assertThat(page.getContent())
                .extracting("customerId")
                .contains(ob1.customerId(), ob2.customerId());
    }

    // ─── 16. Cumulative balance reduction ─────────────────────────────────────

    @Test
    @DisplayName("Three sequential payments reduce balance cumulatively")
    void multiplePayments_cumulativeBalanceReduction() {
        String aliceId = onboardAndComplete("alice.seq@example.com", "+447700910001", "ALICEK1234F");
        String bobId   = onboardAndComplete("bob.seq@example.com",   "+447700910002", "BOBJ01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("30000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("1000"),  "GBP");

        for (int i = 0; i < 3; i++) {
            PaymentResponse p = paymentService.initiatePayment(new InitiatePaymentRequest(
                    aliceId, aliceAccId, bobAccId,
                    new BigDecimal("1000"), "GBP", Payment.PaymentType.IMPS, "payment " + i));
            paymentService.processPayment(p.paymentId());
        }

        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("27000.00");
        assertThat(accountService.getBalance(bobAccId).balance()).isEqualByComparingTo("4000.00");
    }

    // ─── 17. Holds prevent double-spending ───────────────────────────────────

    @Test
    @DisplayName("Existing hold reduces available funds and prevents further overspend")
    void existingHold_preventsFurtherOverspend() {
        String aliceId = onboardAndComplete("alice.holds@example.com", "+447700911001", "ALICEL1234F");
        String bobId   = onboardAndComplete("bob.holds@example.com",   "+447700911002", "BOBK01234G");
        String aliceAccId = openAccount(aliceId, Account.AccountType.CURRENT, new BigDecimal("5000"), "GBP");
        String bobAccId   = openAccount(bobId,   Account.AccountType.CURRENT, new BigDecimal("500"),  "GBP");

        // First payment places 3000 hold → 2000 available
        PaymentResponse pending = paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("3000"), "GBP", Payment.PaymentType.IMPS, "first"));

        // Second payment for 2500 should fail (only 2000 available)
        assertThatThrownBy(() -> paymentService.initiatePayment(new InitiatePaymentRequest(
                aliceId, aliceAccId, bobAccId,
                new BigDecimal("2500"), "GBP", Payment.PaymentType.IMPS, "overdraft attempt")))
            .isInstanceOf(InsufficientFundsException.class);

        // Clean up the first hold
        paymentService.failPayment(pending.paymentId(), "cancelled");

        // After hold released, balance restored
        assertThat(accountService.getBalance(aliceAccId).balance()).isEqualByComparingTo("5000.00");
        assertThat(accountService.getBalance(aliceAccId).holdAmount()).isEqualByComparingTo("0.00");
    }
}
