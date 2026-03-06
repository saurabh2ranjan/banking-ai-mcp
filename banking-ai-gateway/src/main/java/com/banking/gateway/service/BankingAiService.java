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

        history.add(new UserMessage(userMessage));
        trimHistory(history);

        String response = bankingChatClient.prompt()
                .messages(history)
                .call()
                .content();

        assert response != null;
        history.add(new AssistantMessage(response));
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
