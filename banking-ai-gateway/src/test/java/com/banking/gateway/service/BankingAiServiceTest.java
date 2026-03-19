package com.banking.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankingAiServiceTest {

    // ── mocks for fluent ChatClient chain ─────────────────────────────────────
    @Mock ChatClient                            bankingChatClient;
    @Mock ChatClient.ChatClientRequestSpec      requestSpec;
    @Mock ChatClient.CallResponseSpec           callSpec;

    BankingAiService service;

    @BeforeEach
    void setUp() {
        service = new BankingAiService(bankingChatClient);
    }

    // ── helper: wire up the full fluent chain ─────────────────────────────────
    private void mockChatResponse(String response) {
        when(bankingChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(Collections.singletonList(any()))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }

    private void mockSingleQueryResponse(String response) {
        when(bankingChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }

    // ── chat() — basic interaction ────────────────────────────────────────────

    @Test
    void chat_validMessage_returnsAiResponse() {
        mockChatResponse("Balance of ACC-001 is £10,000");

        String response = service.chat("session-1", "What is my balance?");

        assertThat(response).isEqualTo("Balance of ACC-001 is £10,000");
    }

    @Test
    void chat_addsUserMessageToHistory() {
        mockChatResponse("AI response");

        service.chat("session-1", "Hello");

        List<Map<String, String>> history = service.getSessionHistory("session-1");
        assertThat(history).hasSize(2); // user + assistant
        assertThat(history.get(0).get("role")).isEqualTo(MessageType.USER.getValue());
        assertThat(history.get(0).get("content")).isEqualTo("Hello");
    }

    @Test
    void chat_addsAssistantResponseToHistory() {
        mockChatResponse("AI response");

        service.chat("session-1", "Hello");

        List<Map<String, String>> history = service.getSessionHistory("session-1");
        assertThat(history.get(1).get("role")).isEqualTo(MessageType.ASSISTANT.getValue());
        assertThat(history.get(1).get("content")).isEqualTo("AI response");
    }

    @Test
    void chat_multiTurn_accumulatesHistory() {
        mockChatResponse("Response 1");
        service.chat("session-1", "Message 1");

        mockChatResponse("Response 2");
        service.chat("session-1", "Message 2");

        List<Map<String, String>> history = service.getSessionHistory("session-1");
        assertThat(history).hasSize(4); // 2 user + 2 assistant messages
    }

    @Test
    void chat_differentSessions_haveIsolatedHistories() {
        mockChatResponse("Response for Alice");
        service.chat("alice-session", "Alice message");

        mockChatResponse("Response for Bob");
        service.chat("bob-session", "Bob message");

        List<Map<String, String>> aliceHistory = service.getSessionHistory("alice-session");
        List<Map<String, String>> bobHistory   = service.getSessionHistory("bob-session");

        assertThat(aliceHistory).hasSize(2);
        assertThat(bobHistory).hasSize(2);
        assertThat(aliceHistory.get(0).get("content")).isEqualTo("Alice message");
        assertThat(bobHistory.get(0).get("content")).isEqualTo("Bob message");
    }

    @Test
    void chat_sendsFullHistoryToAiOnEachCall() {
        mockChatResponse("Response 1");
        service.chat("session-1", "Message 1");

        mockChatResponse("Response 2");
        service.chat("session-1", "Message 2");

        // second call should send history with 3 messages (user1 + assistant1 + user2)
        verify(requestSpec, times(2)).messages(Collections.singletonList(any()));
    }

    // ── chat() — trimHistory ──────────────────────────────────────────────────

    @Test
    void chat_historyExceeds50Messages_oldestMessagesDropped() {
        mockChatResponse("response");

        // fill history to MAX_HISTORY_SIZE (50) — each chat() adds 2 messages
        for (int i = 0; i < 25; i++) {
            service.chat("session-trim", "message-" + i);
        }
        assertThat(service.getSessionHistory("session-trim")).hasSize(50);

        // one more chat() → adds user message (51) → trim removes oldest → then adds assistant (50+1-1=50)
        service.chat("session-trim", "message-overflow");

        assertThat(service.getSessionHistory("session-trim")).hasSize(51);
    }

    @Test
    void chat_historyTrimmed_mostRecentMessagesRetained() {
        mockChatResponse("response");

        for (int i = 0; i < 25; i++) {
            service.chat("session-trim", "message-" + i);
        }

        // one more to trigger trim
        service.chat("session-trim", "latest-message");

        List<Map<String, String>> history = service.getSessionHistory("session-trim");
        // latest user message should be in history
        assertThat(history.stream()
                .anyMatch(m -> "latest-message".equals(m.get("content"))))
                .isTrue();
        // very first message should have been dropped
        assertThat(history.stream()
                .anyMatch(m -> "message-0".equals(m.get("content"))))
                .isFalse();
    }

    // ── chat() — enforceSessionLimit ──────────────────────────────────────────

    @Test
    void chat_sessionsReachLimit_oldestSessionEvicted() {
        mockChatResponse("response");

        // fill up to MAX_SESSIONS (1000)
        for (int i = 0; i < 1000; i++) {
            service.chat("session-" + i, "message");
        }

        Map<String, Object> statsBefore = service.getSessionStats();
        assertThat((int) statsBefore.get("activeSessions")).isEqualTo(1000);

        // one more session — should evict one
        service.chat("session-overflow", "message");

        Map<String, Object> statsAfter = service.getSessionStats();
        assertThat((int) statsAfter.get("activeSessions")).isEqualTo(1000);
    }

    // ── singleQuery() ─────────────────────────────────────────────────────────

    @Test
    void singleQuery_returnsAiResponse() {
        mockSingleQueryResponse("Single query response");

        String response = service.singleQuery("What is KYC?");

        assertThat(response).isEqualTo("Single query response");
    }

    @Test
    void singleQuery_doesNotCreateSession() {
        mockSingleQueryResponse("response");

        service.singleQuery("What is KYC?");

        assertThat(service.getSessionStats().get("activeSessions")).isEqualTo(0);
    }

    @Test
    void singleQuery_doesNotAffectExistingSession() {
        mockChatResponse("chat response");
        service.chat("session-1", "chat message");

        mockSingleQueryResponse("single response");
        service.singleQuery("standalone question");

        // session-1 should still have only 2 messages
        assertThat(service.getSessionHistory("session-1")).hasSize(2);
    }

    // ── clearSession() ────────────────────────────────────────────────────────

    @Test
    void clearSession_removesSessionFromMap() {
        mockChatResponse("response");
        service.chat("session-1", "message");

        service.clearSession("session-1");

        assertThat(service.getSessionHistory("session-1")).isEmpty();
        assertThat(service.getSessionStats().get("activeSessions")).isEqualTo(0);
    }

    @Test
    void clearSession_unknownSession_doesNotThrow() {
        // should silently succeed for non-existent session
        service.clearSession("non-existent-session");

        assertThat(service.getSessionStats().get("activeSessions")).isEqualTo(0);
    }

    @Test
    void clearSession_onlyRemovesTargetSession() {
        mockChatResponse("response");
        service.chat("session-1", "message");
        service.chat("session-2", "message");

        service.clearSession("session-1");

        assertThat(service.getSessionHistory("session-1")).isEmpty();
        assertThat(service.getSessionHistory("session-2")).hasSize(2);
        assertThat(service.getSessionStats().get("activeSessions")).isEqualTo(1);
    }

    // ── getSessionHistory() ───────────────────────────────────────────────────

    @Test
    void getSessionHistory_unknownSession_returnsEmptyList() {
        List<Map<String, String>> history = service.getSessionHistory("non-existent");

        assertThat(history).isEmpty();
    }

    @Test
    void getSessionHistory_returnsCorrectRoleAndContent() {
        mockChatResponse("AI said this");

        service.chat("session-1", "User said this");

        List<Map<String, String>> history = service.getSessionHistory("session-1");
        assertThat(history).hasSize(2);

        Map<String, String> userMsg = history.get(0);
        assertThat(userMsg.get("role")).isEqualTo(MessageType.USER.getValue());
        assertThat(userMsg.get("content")).isEqualTo("User said this");

        Map<String, String> assistantMsg = history.get(1);
        assertThat(assistantMsg.get("role")).isEqualTo(MessageType.ASSISTANT.getValue());
        assertThat(assistantMsg.get("content")).isEqualTo("AI said this");
    }

    @Test
    void getSessionHistory_returnsMessagesInOrder() {
        mockChatResponse("Response 1");
        service.chat("session-1", "Question 1");

        mockChatResponse("Response 2");
        service.chat("session-1", "Question 2");

        List<Map<String, String>> history = service.getSessionHistory("session-1");
        assertThat(history.get(0).get("content")).isEqualTo("Question 1");
        assertThat(history.get(1).get("content")).isEqualTo("Response 1");
        assertThat(history.get(2).get("content")).isEqualTo("Question 2");
        assertThat(history.get(3).get("content")).isEqualTo("Response 2");
    }

    // ── getSessionStats() ─────────────────────────────────────────────────────

    @Test
    void getSessionStats_noSessions_returnsZeroStats() {
        Map<String, Object> stats = service.getSessionStats();

        assertThat(stats.get("activeSessions")).isEqualTo(0);
        assertThat(stats.get("totalMessages")).isEqualTo(0);
        assertThat((List<?>) stats.get("sessionIds")).isEmpty();
    }

    @Test
    void getSessionStats_multipleSessions_returnsCorrectCounts() {
        mockChatResponse("response");
        service.chat("session-a", "msg1");
        service.chat("session-a", "msg2");
        service.chat("session-b", "msg1");

        Map<String, Object> stats = service.getSessionStats();

        assertThat(stats.get("activeSessions")).isEqualTo(2);
        // session-a: 4 messages (2 user + 2 assistant), session-b: 2 messages
        assertThat(stats.get("totalMessages")).isEqualTo(6);
    }

    @Test
    void getSessionStats_sessionIdsAreSorted() {
        mockChatResponse("response");
        service.chat("session-c", "msg");
        service.chat("session-a", "msg");
        service.chat("session-b", "msg");

        Map<String, Object> stats = service.getSessionStats();
        List<?> sessionIds = (List<?>) stats.get("sessionIds");

        String asString = sessionIds.toString();
        assertThat(asString).contains("session-a", "session-b", "session-c");
        // verify sorted order
        assertThat(asString.indexOf("session-a")).isLessThan(asString.indexOf("session-b"));
        assertThat(asString.indexOf("session-b")).isLessThan(asString.indexOf("session-c"));
    }

    @Test
    void getSessionStats_afterClear_updatesCorrectly() {
        mockChatResponse("response");
        service.chat("session-1", "msg");
        service.chat("session-2", "msg");

        service.clearSession("session-1");

        Map<String, Object> stats = service.getSessionStats();
        assertThat(stats.get("activeSessions")).isEqualTo(1);
        assertThat(stats.get("totalMessages")).isEqualTo(2);
        assertThat(stats.get("sessionIds").toString()).contains("session-2");
    }
}