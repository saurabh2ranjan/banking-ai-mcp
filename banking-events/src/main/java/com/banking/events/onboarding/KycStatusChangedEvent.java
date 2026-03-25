package com.banking.events.onboarding;

import com.banking.events.EventMetadata;

/**
 * Published when a customer's KYC status changes (VERIFIED, REJECTED, etc.).
 * Partition key: customerId.
 */
public record KycStatusChangedEvent(
        String customerId,
        String email,
        String fullName,
        String previousKycStatus,
        String newKycStatus,
        String rejectionReason,
        EventMetadata metadata
) {
}
