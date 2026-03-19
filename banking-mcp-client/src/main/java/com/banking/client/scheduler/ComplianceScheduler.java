package com.banking.client.scheduler;

import com.banking.client.service.BankingMcpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled compliance jobs that invoke the banking MCP server directly.
 *
 * Use case: compliance teams need periodic KYC status checks without
 * going through the AI chat interface. This hits the tool layer directly —
 * deterministic, fast, and auditable.
 *
 * Disabled when banking.compliance.kyc-check-enabled=false (e.g. in tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.compliance.kyc-check-enabled", havingValue = "true", matchIfMissing = false)
public class ComplianceScheduler {

    private final BankingMcpClientService mcpClientService;

    /**
     * Every hour: fetch the first page of KYC-pending customers and log them.
     * In a real system, this would push to a compliance dashboard or send alerts.
     */
    @Scheduled(fixedRateString = "PT1H")
    public void runHourlyKycCheck() {
        log.info("=== Hourly KYC compliance check starting ===");
        try {
            String result = mcpClientService.getPendingKycCustomers(0, 20);
            log.info("KYC pending customers: {}", result);
        } catch (Exception e) {
            log.error("KYC compliance check failed", e);
        }
    }

    /**
     * Every 5 minutes: log a tool availability ping to detect server outages early.
     */
    @Scheduled(fixedRateString = "PT5M")
    public void pingServerHealth() {
        try {
            var tools = mcpClientService.listAvailableTools();
            log.debug("MCP server healthy — {} tools available", tools.size());
        } catch (Exception e) {
            log.error("MCP server connectivity check FAILED — banking-ai-gateway may be down", e);
        }
    }
}
