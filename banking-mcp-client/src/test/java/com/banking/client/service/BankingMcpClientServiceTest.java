package com.banking.client.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankingMcpClientServiceTest {

    @Mock
    McpSyncClient mcpSyncClient;

    @InjectMocks
    BankingMcpClientService service;

    // ---- helpers ----

    private McpSchema.ListToolsResult toolList(String... names) {
        // McpSchema.Tool record: (name, title, description, inputSchema, meta, annotations, extensions)
        List<McpSchema.Tool> tools = java.util.Arrays.stream(names)
                .<McpSchema.Tool>map(n -> new McpSchema.Tool(n, null, "", null, null, null, null))
                .toList();
        return new McpSchema.ListToolsResult(tools, null);
    }

    private McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)), false);
    }

    // ---- tests ----

    @Test
    void listAvailableTools_returnsToolNames() {
        when(mcpSyncClient.listTools(null))
                .thenReturn(toolList("block_account", "get_account_balance", "analyse_payment_fraud_risk"));

        List<String> tools = service.listAvailableTools();

        assertThat(tools).containsExactly(
                "block_account", "get_account_balance", "analyse_payment_fraud_risk");
    }

    @Test
    void blockAccount_invokesCorrectTool() {
        when(mcpSyncClient.callTool(any())).thenReturn(textResult("{\"status\":\"BLOCKED\"}"));

        String result = service.blockAccount("ACC123", "fraud detected");

        assertThat(result).isEqualTo("{\"status\":\"BLOCKED\"}");
        verify(mcpSyncClient).callTool(argThat(req ->
                req.name().equals("block_account")
                && req.arguments().get("accountId").equals("ACC123")
                && req.arguments().get("reason").equals("fraud detected")));
    }

    @Test
    void analysePaymentFraudRisk_invokesCorrectTool() {
        when(mcpSyncClient.callTool(any())).thenReturn(textResult("{\"fraudScore\":0.85,\"riskLevel\":\"HIGH\"}"));

        String result = service.analysePaymentFraudRisk("PAY-001");

        assertThat(result).contains("fraudScore");
        verify(mcpSyncClient).callTool(argThat(req ->
                req.name().equals("analyse_payment_fraud_risk")
                && req.arguments().get("paymentId").equals("PAY-001")));
    }

    @Test
    void getPendingKycCustomers_passesPageAndSize() {
        when(mcpSyncClient.callTool(any())).thenReturn(textResult("{\"customers\":[]}"));

        service.getPendingKycCustomers(1, 10);

        verify(mcpSyncClient).callTool(argThat(req ->
                req.name().equals("get_pending_kyc_customers")
                && req.arguments().get("page").equals(1)
                && req.arguments().get("size").equals(10)));
    }

    @Test
    void holdPaymentForFraud_passesAllArguments() {
        when(mcpSyncClient.callTool(any())).thenReturn(textResult("{\"status\":\"HELD\"}"));

        service.holdPaymentForFraud("PAY-002", 0.75, "HIGH", "velocity spike");

        verify(mcpSyncClient).callTool(argThat(req ->
                req.name().equals("hold_payment_for_fraud")
                && req.arguments().get("fraudScore").equals(0.75)
                && req.arguments().get("riskLevel").equals("HIGH")));
    }

    @Test
    void invokeTool_delegatesDirectly() {
        when(mcpSyncClient.callTool(any())).thenReturn(textResult("ok"));

        String result = service.invokeTool("get_account_balance", Map.of("accountId", "ACC-X"));

        assertThat(result).isEqualTo("ok");
        verify(mcpSyncClient).callTool(argThat(req ->
                req.name().equals("get_account_balance")));
    }
}