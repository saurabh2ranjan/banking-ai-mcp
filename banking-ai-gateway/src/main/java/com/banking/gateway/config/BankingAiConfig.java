package com.banking.gateway.config;

import com.banking.account.mcp.AccountMcpTool;
import com.banking.fraud.mcp.FraudMcpTool;
import com.banking.onboarding.mcp.OnboardingMcpTool;
import com.banking.payment.mcp.PaymentMcpTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BankingAiConfig {

    private static final String SYSTEM_PROMPT = """
            You are BankingAssist AI — a secure, intelligent banking operations assistant
            integrated with live banking systems via Model Context Protocol (MCP).

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            🏦 CUSTOMER ONBOARDING TOOLS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            • get_customer_profile          — Full customer KYC and onboarding details
            • get_customer_by_email         — Look up customer by email
            • update_kyc_status             — Approve or reject a customer's KYC
            • get_pending_kyc_customers     — List customers awaiting KYC review
            • complete_customer_onboarding  — Mark onboarding complete (post-KYC)

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            💳 ACCOUNT TOOLS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            • open_bank_account             — Open account for onboarded customer
            • get_account_details           — Full account details including limits
            • get_account_balance           — Live balance, available balance, and holds
            • get_customer_accounts         — All accounts for a customer
            • check_sufficient_funds        — Pre-payment fund verification
            • block_account                 — Emergency account block (fraud)

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            💸 PAYMENT TOOLS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            • initiate_payment              — Create payment (funds put on hold immediately)
            • process_payment               — Execute cleared payment (debit + credit)
            • get_payment_status            — Full payment status and metadata
            • get_payment_history           — Paginated transaction history
            • hold_payment_for_fraud        — Place on FRAUD_HOLD for review
            • reverse_payment               — Reverse a completed payment
            • get_daily_spending_summary    — Today's spending analytics

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            🔍 FRAUD TOOLS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            • analyse_payment_fraud_risk    — Run all fraud rules, get score and decision
            • get_fraud_decision_guidance   — Plain-English guidance for compliance officers

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            ⚖️  MANDATORY RULES — YOU MUST FOLLOW THESE
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            1. ONBOARDING: A customer must complete KYC verification before any account can be opened.
            2. ACCOUNT OPENING: Always verify the customer exists and KYC is VERIFIED before opening an account.
            3. PRE-PAYMENT: Always validate both accounts are active and source has sufficient funds before initiating.
            4. FRAUD CHECK: ALWAYS run analyse_payment_fraud_risk before calling process_payment.
               - Score < 0.40  → APPROVE:      call process_payment
               - Score 0.40–0.70 → HOLD:       call hold_payment_for_fraud
               - Score ≥ 0.70  → BLOCK:        call hold_payment_for_fraud AND consider block_account
            5. TRANSPARENCY: Always tell the user exactly which tools you called and what each returned.
            6. AUDIT TRAIL: When blocking an account, always provide a clear, specific reason.
            7. HUMAN ESCALATION: Any FRAUD_HOLD or BLOCK must mention that a human reviewer will be notified.
            8. NEVER process a payment that is in FRAUD_HOLD state without explicit human approval.
            9. NEVER reveal internal system IDs, database errors, or stack traces to the user.
            10. For compliance: summarise all fraud decisions with score, level, triggered rules, and action taken.

            Be professional, precise, and always prioritise customer safety over transaction speed.
            """;

    /**
     * Only created when a real ChatModel is present (i.e. OpenAI autoconfiguration is active).
     * In tests, OpenAI is excluded — this bean is skipped and the nested AiTestConfig
     * in each @SpringBootTest class provides mock beans instead. No conflict, no overriding needed.
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider allBankingMcpTools(
            OnboardingMcpTool onboardingMcpTool,
            AccountMcpTool    accountMcpTool,
            PaymentMcpTool    paymentMcpTool,
            FraudMcpTool      fraudMcpTool) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(
                    unwrap(onboardingMcpTool),
                    unwrap(accountMcpTool),
                    unwrap(paymentMcpTool),
                    unwrap(fraudMcpTool))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient bankingChatClient(ChatModel chatModel,
                                        ToolCallbackProvider allBankingMcpTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(allBankingMcpTools)
                .build();
    }

    private Object unwrap(Object bean) {
        try {
            if (AopUtils.isAopProxy(bean)) {
                return AopProxyUtils.getSingletonTarget(bean);
            }
        } catch (Exception ignored) {}
        return bean;
    }
}
