package com.banking.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankingAiService {

    private final ChatClient bankingChatClient;

    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 50;
    private static final int MAX_SESSIONS     = 1000;

    public String chat(String sessionId, String userMessage) {
        log.info("[Session:{}] User: {}", sessionId, userMessage);

        enforceSessionLimit();
        List<Message> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        history.add(new UserMessage(userMessage)); // 1. append user msg to history
        trimHistory(history);

        String response = bankingChatClient.prompt()
                .messages(history) // 2. send ENTIRE history to GPT-4o
                .call()
                .content();        // 3. get final text response

        /*
                ### The Tool Call Loop (inside `.call()`)

                This is the part that's invisible in the code but critical:
                ```
                .call() internally does:
                │
                ├─ Send messages + available MCP tools to GPT-4o
                │
                ├─ GPT-4o responds with a tool_call? (e.g. checkBalance, fraudCheck)
                │   ├─ YES → Spring AI executes the tool → feeds result back to GPT-4o
                │   │         (loops until no more tool calls)
                │   └─ NO  → Returns final text content
                │
                └─ .content() returns the final human-readable string
                 We never see this loop in the code — Spring AI abstracts it entirely.

                 ### Full Request Lifecycle (e.g. "Send $5000 from ACC-001 to ACC-002")
                    ```
                    HTTP POST /chat
                        → BankingAiController.chat()
                        → BankingAiService.chat(sessionId, message)
                            → history.add(UserMessage)
                            → bankingChatClient.prompt().messages(history).call()
                                ┌─ Spring AI sends: system prompt + history + tool definitions → GPT-4o
                                ├─ GPT-4o calls: check_sufficient_funds(ACC-001, 5000)
                                │   └─ Spring AI invokes AccountMcpTool.checkSufficientFunds() → result
                                ├─ GPT-4o calls: analyse_payment_fraud_risk(...)
                                │   └─ Spring AI invokes FraudMcpTool.analysePaymentFraudRisk() → score: 0.21
                                ├─ GPT-4o (score < 0.40): calls initiate_payment(...)
                                │   └─ Spring AI invokes PaymentMcpTool.initiatePayment() → paymentId
                                ├─ GPT-4o calls: process_payment(paymentId)
                                │   └─ Spring AI invokes PaymentMcpTool.processPayment() → SUCCESS
                                └─ GPT-4o returns final human-readable summary
                            → history.add(AssistantMessage(response))
                        ← "Transfer of $5000 completed. Fraud score was 0.21 (LOW). Tools called: ..."
                    The entire tool-call loop is managed by Spring AI — the service code is just 10 lines.
         */

        assert response != null;
        history.add(new AssistantMessage(response));  // 4. append AI reply to history
        log.info("[Session:{}] AI replied ({} chars)", sessionId, response.length());
        return response;
    }

    public String singleQuery(String message) {
        return bankingChatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Session cleared: {}", sessionId);
    }

    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of())
                .stream()
                .map(m -> Map.of(
                    "role",    m.getMessageType().getValue(),
                    "content", m.getText()
                ))
                .toList();
    }

    public Map<String, Object> getSessionStats() {
        return Map.of(
            "activeSessions", sessions.size(),
            "sessionIds",     sessions.keySet().stream().sorted().toList(),
            "totalMessages",  sessions.values().stream().mapToInt(List::size).sum()
        );
    }

    /*
     This drops oldest messages when a session exceeds 50 messages,
        preventing unbounded token growth sent to GPT-4o.
     */
    private void trimHistory(List<Message> history) {
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    private void enforceSessionLimit() {
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.keySet().stream().findFirst().ifPresent(sessions::remove);
        }
    }
}
