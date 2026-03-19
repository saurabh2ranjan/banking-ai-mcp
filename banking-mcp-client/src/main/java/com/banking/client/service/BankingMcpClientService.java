package com.banking.client.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP Client service — invokes banking tools directly over the MCP protocol.
 * No AI model is involved: this bypasses GPT-4o and calls the tool layer directly.
 * Useful for deterministic, programmatic, or scheduled operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingMcpClientService {

    // Auto-configured by spring-ai-starter-mcp-client-webmvc from application.yml connections
    private final McpSyncClient mcpSyncClient;

    // -------------------------------------------------------------------------
    // Tool Discovery
    // -------------------------------------------------------------------------

    /**
     * Lists all tools exposed by the banking MCP server.
     * Use this at startup or in admin endpoints to verify server connectivity.
     */
    public List<String> listAvailableTools() {
        McpSchema.ListToolsResult result = mcpSyncClient.listTools(null);
        return result.tools().stream()
                .map(McpSchema.Tool::name)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Use Case 1 — Compliance Automation (KYC)
    // Called by ComplianceScheduler on a fixed schedule.
    // -------------------------------------------------------------------------

    /**
     * Fetches customers with pending KYC status.
     * Returns raw tool output — caller decides how to route/alert.
     */
    public String getPendingKycCustomers(int page, int size) {
        log.info("Compliance check: fetching pending KYC customers (page={}, size={})", page, size);
        return callTool("get_pending_kyc_customers", Map.of("page", page, "size", size));
    }

    /**
     * Approves KYC for a customer after compliance review.
     */
    public String approveKyc(String customerId, String reason) {
        log.info("Approving KYC for customer={}", customerId);
        return callTool("update_kyc_status",
                Map.of("customerId", customerId, "kycStatus", "APPROVED", "reason", reason));
    }

    // -------------------------------------------------------------------------
    // Use Case 2 — Admin / Ops Operations
    // Direct tool invocation with no AI reasoning needed.
    // -------------------------------------------------------------------------

    /**
     * Blocks an account immediately — used by ops during incident response.
     */
    public String blockAccount(String accountId, String reason) {
        log.warn("Admin: blocking account={}, reason={}", accountId, reason);
        return callTool("block_account", Map.of("accountId", accountId, "reason", reason));
    }

    /**
     * Gets account balance directly — useful for admin dashboards / monitoring.
     */
    public String getAccountBalance(String accountId) {
        return callTool("get_account_balance", Map.of("accountId", accountId));
    }

    /**
     * Gets daily spending summary for an account.
     */
    public String getDailySpendingSummary(String accountId) {
        return callTool("get_daily_spending_summary", Map.of("accountId", accountId));
    }

    // -------------------------------------------------------------------------
    // Use Case 3 — Fraud Monitoring
    // Compliance/fraud teams trigger these without needing the chat interface.
    // -------------------------------------------------------------------------

    /**
     * Runs fraud risk analysis for a payment.
     * Returns structured fraud score + risk level from the 6 rule engines.
     */
    public String analysePaymentFraudRisk(String paymentId) {
        log.info("Fraud analysis requested for paymentId={}", paymentId);
        return callTool("analyse_payment_fraud_risk", Map.of("paymentId", paymentId));
    }

    /**
     * Gets fraud decision guidance (APPROVE / HOLD / BLOCK recommendation).
     */
    public String getFraudDecisionGuidance(String paymentId) {
        return callTool("get_fraud_decision_guidance", Map.of("paymentId", paymentId));
    }

    /**
     * Holds a payment flagged by an external fraud signal.
     */
    public String holdPaymentForFraud(String paymentId, double fraudScore,
                                      String riskLevel, String reason) {
        log.warn("Holding payment={} for fraud, score={}, risk={}", paymentId, fraudScore, riskLevel);
        return callTool("hold_payment_for_fraud",
                Map.of("paymentId", paymentId,
                       "fraudScore", fraudScore,
                       "riskLevel", riskLevel,
                       "reason", reason));
    }

    // -------------------------------------------------------------------------
    // Use Case 4 — Generic Tool Invocation (admin / integration testing)
    // -------------------------------------------------------------------------

    /**
     * Invokes any registered MCP tool by name with arbitrary arguments.
     * Useful for integration test harnesses and admin tooling.
     */
    public String invokeTool(String toolName, Map<String, Object> arguments) {
        log.info("Generic tool invocation: tool={}", toolName);
        return callTool(toolName, arguments);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private String callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = mcpSyncClient.callTool(
                new McpSchema.CallToolRequest(toolName, arguments));

        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .reduce("", (a, b) -> a + b);
    }
}