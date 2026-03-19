package com.banking.client.controller;

import com.banking.client.service.BankingMcpClientService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API that exposes MCP client operations to internal consumers
 * (e.g. admin dashboard, fraud ops UI, compliance portal).
 *
 * All endpoints invoke the banking MCP server directly — no AI involved.
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Validated
public class BankingMcpClientController {

    private final BankingMcpClientService mcpClientService;

    // ---- Tool Discovery ----

    @GetMapping("/tools")
    public ResponseEntity<List<String>> listTools() {
        return ResponseEntity.ok(mcpClientService.listAvailableTools());
    }

    // ---- Generic Invocation (integration testing / admin) ----

    @PostMapping("/tools/{toolName}")
    public ResponseEntity<String> invokeTool(
            @PathVariable @NotBlank String toolName,
            @RequestBody Map<String, Object> arguments) {
        return ResponseEntity.ok(mcpClientService.invokeTool(toolName, arguments));
    }

    // ---- Compliance ----

    @GetMapping("/compliance/kyc/pending")
    public ResponseEntity<String> getPendingKycCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(mcpClientService.getPendingKycCustomers(page, size));
    }

    @PostMapping("/compliance/kyc/{customerId}/approve")
    public ResponseEntity<String> approveKyc(
            @PathVariable @NotBlank String customerId,
            @RequestParam @NotBlank String reason) {
        return ResponseEntity.ok(mcpClientService.approveKyc(customerId, reason));
    }

    // ---- Admin / Ops ----

    @PostMapping("/admin/accounts/{accountId}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable @NotBlank String accountId,
            @RequestParam @NotBlank String reason) {
        return ResponseEntity.ok(mcpClientService.blockAccount(accountId, reason));
    }

    @GetMapping("/admin/accounts/{accountId}/balance")
    public ResponseEntity<String> getAccountBalance(@PathVariable @NotBlank String accountId) {
        return ResponseEntity.ok(mcpClientService.getAccountBalance(accountId));
    }

    @GetMapping("/admin/accounts/{accountId}/spending-summary")
    public ResponseEntity<String> getDailySpendingSummary(@PathVariable @NotBlank String accountId) {
        return ResponseEntity.ok(mcpClientService.getDailySpendingSummary(accountId));
    }

    // ---- Fraud Ops ----

    @GetMapping("/fraud/payments/{paymentId}/analyse")
    public ResponseEntity<String> analysePaymentFraudRisk(@PathVariable @NotBlank String paymentId) {
        return ResponseEntity.ok(mcpClientService.analysePaymentFraudRisk(paymentId));
    }

    @GetMapping("/fraud/payments/{paymentId}/guidance")
    public ResponseEntity<String> getFraudDecisionGuidance(@PathVariable @NotBlank String paymentId) {
        return ResponseEntity.ok(mcpClientService.getFraudDecisionGuidance(paymentId));
    }

    @PostMapping("/fraud/payments/{paymentId}/hold")
    public ResponseEntity<String> holdPaymentForFraud(
            @PathVariable @NotBlank String paymentId,
            @RequestParam double fraudScore,
            @RequestParam @NotBlank String riskLevel,
            @RequestParam @NotBlank String reason) {
        return ResponseEntity.ok(
                mcpClientService.holdPaymentForFraud(paymentId, fraudScore, riskLevel, reason));
    }
}