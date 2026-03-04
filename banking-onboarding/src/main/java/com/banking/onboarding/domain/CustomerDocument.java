package com.banking.onboarding.domain;

import com.banking.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "customer_documents",
       indexes = @Index(name = "idx_doc_customer", columnList = "customer_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "document_url", length = 500)
    private String documentUrl;       // S3 URL in production

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    public enum DocumentType {
        PASSPORT, NATIONAL_ID, DRIVING_LICENSE,
        UTILITY_BILL, BANK_STATEMENT,
        PAN_CARD, AADHAAR, SALARY_SLIP, INCOME_TAX_RETURN
    }

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED, EXPIRED }

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }
}
